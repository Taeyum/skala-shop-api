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

NET=$(docker inspect "$(docker compose -f "$ROOT/docker-compose.yml" ps -q app)" \
        --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null)

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
  echo "# 호스트      : $(uname -srm) / $(sysctl -n machdep.cpu.brand_string 2>/dev/null) / 코어 $(sysctl -n hw.ncpu 2>/dev/null)"
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
  -v "$ROOT/docs/perf:/perf:ro" -v "$OUT:/out" \
  -e BASE=http://app:8080 "$@" \
  grafana/k6:latest run --summary-export="/out/$NAME.json" "/perf/$(basename "$SCRIPT")" \
  2>&1 | tee -a "$OUT/$NAME.txt"
