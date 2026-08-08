#!/bin/bash
# k6 실행 래퍼.
#
# ★ k6는 데이터를 만든다. Phase 4에서 변이 스크립트가 타임아웃 강제 종료로
#   운영 코드에 변이를 남긴 사고와 같은 부류다. 그래서 안전망을 도구의 일부로 둔다.
#     1) 측정 전 DB를 초기화한다 (앱 재기동 → ddl-auto=create 가 스키마와 시드를 새로 만든다)
#     2) trap 으로 중단·오류 시에도 정리한다
#     3) 원복은 프로세스 밖 수단(docker compose)에 의존한다 — SIGKILL 로도 남지 않는다
#
# 사용법: run.sh <스크립트> <결과이름> [k6 환경변수...]
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT=$1; NAME=$2; shift 2
OUT="$ROOT/docs/evidence/perf"
mkdir -p "$OUT"

# ── Windows(Git Bash) 대응 ────────────────────────────────────────────────
# MSYS는 인자로 들어온 유닉스식 경로를 Windows 경로로 자동 변환한다.
# 그 변환이 **컨테이너 안 경로까지** 건드린다. 실측(2026-08-08):
#     "/perf/load.js" → "C:/Program Files/Git/perf/load.js"
#     k6: The moduleSpecifier ... couldn't be found on local disk
# 그래서 ① 변환을 끄고 ② 호스트 경로만 Windows 형식으로 준다.
# 리눅스·macOS 에서는 둘 다 무해하다 — 환경변수는 무시되고 hostpath()는 입력을 그대로 돌려준다.
export MSYS_NO_PATHCONV=1
hostpath() {
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) cygpath -m "$1" ;;
    *) printf '%s' "$1" ;;
  esac
}

# 호스트 사양 — 측정 조건 식별용이라 OS마다 다른 명령을 순서대로 시도한다.
# (이전 판은 sysctl 만 써서 macOS 밖에서는 빈 줄이 찍혔다)
host_cpu() {
  sysctl -n machdep.cpu.brand_string 2>/dev/null && return
  grep -m1 'model name' /proc/cpuinfo 2>/dev/null | sed 's/.*: *//' && return
  printf '%s' "${PROCESSOR_IDENTIFIER:-unknown}"
}
host_cores() {
  sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || printf '%s' "${NUMBER_OF_PROCESSORS:-?}"
}

# 네트워크는 실행 중인 서비스에서 알아낸다.
# HA 구성(docker-compose.ha.yml)에는 `app` 서비스가 없고 app1/app2/lb 가 있으므로
# 서비스명과 compose 파일을 환경변수로 바꿀 수 있게 한다. 기본값은 단일 구성 그대로다.
#   PERF_COMPOSE_FILE=docker-compose.ha.yml PERF_APP_SERVICE=lb bash docs/perf/run.sh ...
PERF_COMPOSE_FILE=${PERF_COMPOSE_FILE:-docker-compose.yml}
PERF_APP_SERVICE=${PERF_APP_SERVICE:-app}
# ★ compose 파일 경로도 호스트 형식이어야 한다.
#   MSYS_NO_PATHCONV=1 을 켜둔 상태라 "/c/..." 가 그대로 docker.exe 에 넘어가고
#   docker 는 그걸 "C:\c\..." 로 읽는다 (실측: open C:\c\Users\...\docker-compose.yml).
#   ※ 아래 다른 호출들은 `cd "$ROOT" && docker compose ...` 형태라 bash 가 경로를 처리하므로 안전하다.
#     이 줄만 경로를 **인자로** 넘겨서 걸렸다 — 같은 파일 안에서도 호출 형태에 따라 갈린다.
NET=$(docker inspect "$(docker compose -f "$(hostpath "$ROOT/$PERF_COMPOSE_FILE")" ps -q "$PERF_APP_SERVICE")" \
        --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null)

# 네트워크를 못 찾으면 여기서 멈춘다.
# 빈 값으로 docker run 에 넘기면 "no name set for network" 로 죽는데,
# 그 메시지만으로는 원인이 경로인지 스택이 안 떠 있는 건지 알 수 없다.
if [ -z "$NET" ]; then
  {
    echo "  ❌ compose 네트워크를 찾지 못했다."
    echo "     compose 파일: $PERF_COMPOSE_FILE / 서비스: $PERF_APP_SERVICE"
    echo "     스택이 떠 있는지 확인: docker compose ps"
  } >&2
  exit 1
fi

reset_db() {
  # ddl-auto=create 이므로 앱을 다시 만들면 스키마·시드가 초기 상태로 돌아간다.
  # DB 볼륨을 지우지 않는 이유는 postgres 기동(약 5초)을 매번 반복하지 않기 위해서다
  (cd "$ROOT" && docker compose restart app >/dev/null 2>&1)
  for i in $(seq 1 60); do
    [ "$(docker inspect --format '{{.State.Health.Status}}' \
        "$(cd "$ROOT" && docker compose ps -q app)" 2>/dev/null)" = "healthy" ] && return 0
    sleep 2
  done
  echo "  ✗ 앱이 healthy 가 되지 않았다" >&2; return 1
}

cleanup() {
  echo "  [정리] DB 초기화"
  reset_db || true
}
trap cleanup EXIT INT TERM

echo "  [준비] DB 초기화"
reset_db || exit 1

# ── 측정 조건을 원시 출력과 같은 파일에 박아둔다 ──────────────────────────
# 요약만 남기면 나중에 다른 각도로 볼 때 재해석이 안 된다.
# 커밋 해시가 없으면 "어느 코드에서 잰 수치인가"를 복원할 수 없다.
{
  echo "# ===== 측정 조건 ====="
  echo "# 일시        : $(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "# 커밋        : $(cd "$ROOT" && git rev-parse HEAD) ($(cd "$ROOT" && git describe --tags --always))"
  echo "# 작업트리    : $(cd "$ROOT" && [ -z "$(git status --porcelain)" ] && echo clean || echo '수정됨(dirty)')"
  echo "# 스크립트    : $SCRIPT"
  echo "# k6 인자     : $*"
  echo "# 호스트      : $(uname -srm) / $(host_cpu) / 코어 $(host_cores)"
  echo "# Docker VM   : CPU $(docker info --format '{{.NCPU}}') / MEM $(( $(docker info --format '{{.MemTotal}}') / 1073741824 ))GB"
  echo "# 앱 이미지   : $(docker inspect --format '{{.Config.Image}}' "$(cd "$ROOT" && docker compose ps -q app)" 2>/dev/null)"
  echo "# DB          : $(docker inspect --format '{{.Config.Image}}' "$(cd "$ROOT" && docker compose ps -q postgres)" 2>/dev/null)"
  echo "# 워밍업      : 스크립트 내 warmup 시나리오 참조 (measure 태그만 임계값 적용)"
  echo "# DB 초기화   : 측정 직전 app 재기동 (ddl-auto=create)"
  echo "# ====================="
  echo
} > "$OUT/$NAME.txt"

echo "  [측정] $SCRIPT"
docker run --rm --network "$NET" \
  -v "$(hostpath "$ROOT/docs/perf"):/perf:ro" -v "$(hostpath "$OUT"):/out" \
  -e BASE=http://app:8080 "$@" \
  grafana/k6:latest run --summary-export="/out/$NAME.json" "/perf/$(basename "$SCRIPT")" \
  2>&1 | tee -a "$OUT/$NAME.txt"

# ★ 측정 장치가 실제로 일했는지 확인한다.
# k6가 스크립트를 못 찾거나 대상에 연결하지 못해도 이 스크립트는 여기까지 도달한다.
# 그러면 "조건 헤더만 있고 수치는 없는 파일"이 남고, 목록에서는 측정된 것처럼 보인다.
if ! grep -qE '^\s+http_reqs' "$OUT/$NAME.txt"; then
  {
    echo
    echo "  ❌ k6 요약에 http_reqs 가 없다 — 측정이 성립하지 않았다."
    echo "     빈 결과를 남기면 '측정했다'가 되므로 실패로 끝낸다."
    echo "     원시 출력: $OUT/$NAME.txt"
  } | tee -a "$OUT/$NAME.txt" >&2
  exit 1
fi
