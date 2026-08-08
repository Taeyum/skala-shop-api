#!/bin/bash
# graceful shutdown 효과 측정 (k6 기반) — Phase 5의 curl 판을 대체한다.
#
#   bash docs/perf/shutdown.sh graceful
#   bash docs/perf/shutdown.sh immediate
#
# ★ Phase 5 판과 다른 점
#   ① curl -> k6. curl 은 프로세스 기동(약 10ms+)이 SIGTERM 보다 느려 요청이
#      서버에 닿기 전에 이미 닫혀 있었다. k6 는 VU 가 계속 인플라이트 상태라
#      SIGTERM 순간에 실제로 처리 중인 요청이 존재한다.
#   ② application.yml 을 재작성하지 않는다. Spring 완화 바인딩으로
#      SERVER_SHUTDOWN 환경변수가 server.shutdown 을 덮는다.
#      **측정이 운영 소스를 고치면 안 된다** — Phase 4에서 변이 스크립트가
#      타임아웃 강제 종료로 운영 코드에 변이를 남긴 사고의 교훈이다.
#
# ★ 측정 장치를 먼저 검증한다 — 이 스크립트의 핵심
#   "실패 0" 이 **보호된 것**인지 **그 순간 요청이 없었던 것**인지 구분되지 않으면
#   그 측정은 성립하지 않는다. 이 프로젝트가 반복해서 만난 형태다.
#   그래서 k6 의 요청별 시계열(--out json)을 받아 **SIGTERM 시점에 실제로
#   진행 중이던 요청 수**를 먼저 센다. 그 값이 0이면 측정 자체를 무효로 판정한다.
set -u
MODE=${1:-graceful}
case "$MODE" in graceful|immediate) ;; *) echo "사용법: shutdown.sh <graceful|immediate>"; exit 2;; esac

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/docs/evidence/perf"; mkdir -p "$OUT"
NAME="win-shutdown-$MODE"
export MSYS_NO_PATHCONV=1
hostpath() { case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) cygpath -m "$1";; *) printf '%s' "$1";; esac; }

cleanup() {
  echo "  [정리] 앱 복구"
  (cd "$ROOT" && docker compose up -d app >/dev/null 2>&1)
}
trap cleanup EXIT INT TERM

echo "  [준비] SERVER_SHUTDOWN=$MODE 로 앱 재생성"
(cd "$ROOT" && SERVER_SHUTDOWN="$MODE" docker compose up -d --force-recreate app >/dev/null 2>&1)
for i in $(seq 1 40); do
  [ "$(docker inspect --format '{{.State.Health.Status}}' \
      "$(cd "$ROOT" && docker compose ps -q app)" 2>/dev/null)" = "healthy" ] && break
  sleep 2
done
NET=$(docker inspect "$(cd "$ROOT" && docker compose ps -q app)" \
        --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}')

# 설정이 컨테이너까지 갔는지 먼저 확인한다 (측정 전에 본다)
ENVVAL=$(cd "$ROOT" && docker compose exec -T app printenv SERVER_SHUTDOWN 2>/dev/null | tr -d '\r')
echo "  [확인] 컨테이너의 SERVER_SHUTDOWN = ${ENVVAL:-(없음)}"
if [ "$ENVVAL" != "$MODE" ]; then
  echo "  ❌ 환경변수가 의도한 값이 아니다 — 측정해도 두 조건이 같아진다. 중단한다." >&2
  exit 1
fi

{
  echo "# ===== graceful shutdown 측정 ($MODE) ====="
  echo "# 일시   : $(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "# 커밋   : $(cd "$ROOT" && git rev-parse HEAD)  작업트리: $(cd "$ROOT" && [ -z "$(git status --porcelain)" ] && echo clean || echo dirty)"
  echo "# 설정   : SERVER_SHUTDOWN=$MODE (환경변수 — application.yml 무변경)"
  echo "#          spring.lifecycle.timeout-per-shutdown-phase=20s, stop_grace_period=30s"
  echo "# 시나리오: cache.js THINK=0 VUS=20, 부하 중 SIGTERM(docker compose stop)"
  echo "# ==========================================="
} > "$OUT/$NAME.txt"

# --quiet — k6 진행률 줄이 초당 여러 번 갱신되어 결과 파일이 900KB 까지 불었다.
# 원시 출력을 보존한다는 원칙은 **재해석 가능한 내용**을 두라는 뜻이지
# 진행률 스피너를 쌓아두라는 뜻이 아니다. 요약·분석·종료 로그만 남긴다.
echo "  [측정] 부하 시작 (백그라운드)"
rm -f "$OUT/$NAME.json"
docker run --rm --network "$NET" \
  -v "$(hostpath "$ROOT/docs/perf"):/perf:ro" -v "$(hostpath "$OUT"):/out" \
  -e BASE=http://app:8080 -e VUS=20 -e DURATION=25s -e MODE=plain -e THINK=0 \
  grafana/k6:latest run --quiet --out "json=/out/$NAME.json" /perf/cache.js \
  >> "$OUT/$NAME.txt" 2>&1 &
K6PID=$!

# 워밍업(20초) + measure 시작(25초 지점) 이후로 들어간 뒤 종료시킨다
sleep 32
T=$(python3 -c "import time;print(f'{time.time():.3f}')")
echo "  [SIGTERM] t=$T"
echo "# SIGTERM_EPOCH=$T" >> "$OUT/$NAME.txt"
(cd "$ROOT" && docker compose stop -t 30 app >/dev/null 2>&1)
echo "  [SIGTERM] 완료 t=$(python3 -c "import time;print(f'{time.time():.3f}')")"

# ★ 종료 로그 — graceful 에서만 나오는 문장이 있는지.
#   설정이 실제로 걸렸다는 증거이며, 이게 없으면 "두 조건이 같았다"는 결과를 해석할 수 없다.
#   전에는 `tail -40` 으로 잘랐다가 아무것도 못 잡았다 — ddl-auto=create 때문에 종료 시
#   Hibernate 가 drop 문을 대량으로 찍어 Spring 의 종료 문장이 40줄 밖으로 밀려났다.
#   **전체 로그에서 찾는다.**
#
#   ★★ 그리고 **별도 파일에 쓴 뒤 나중에 합친다.**
#   Windows/MSYS 에서 백그라운드 k6(`>> file &`)와 부모 셸이 같은 파일에 동시에 덧붙이면
#   안전하지 않다 — 자식이 자기 오프셋으로 계속 써 내려가며 중간에 끼워 넣은 줄을 덮는다.
#   실제로 이 블록의 두 줄이 통째로 사라졌다(2026-08-08). 원인을 찾기 전까지
#   "로그가 안 나왔다"로 오해했다.
LOGCHK="$OUT/$NAME.logcheck"
{
  echo "--- 종료 로그 확인 ---"
  (cd "$ROOT" && docker compose logs app 2>&1 \
     | grep -iE "Commencing graceful shutdown|Graceful shutdown complete|Shutdown initiated" \
     | sed 's/^/    /') || true
  echo "--- (graceful 에서만 'Commencing graceful shutdown' 이 나와야 한다) ---"
} > "$LOGCHK" 2>&1

wait $K6PID
echo "  [측정] 종료"

# k6 가 파일을 다 쓴 뒤에 합친다 (동시 append 회피)
cat "$LOGCHK" >> "$OUT/$NAME.txt"; cat "$LOGCHK"; rm -f "$LOGCHK"

# ★ MSYS_NO_PATHCONV=1 을 스크립트 전역에 export 했으므로 **docker 가 아닌 명령의 인자도**
#   변환되지 않는다. python.exe 에 /c/... 를 그대로 넘기면 C:\c\... 로 읽어 파일을 못 찾는다
#   (실측: can't open file 'C:\c\Users\...'). 호스트 경로로 바꿔서 넘긴다.
python3 "$(hostpath "$ROOT/docs/perf/shutdown-analyze.py")" \
        "$(hostpath "$OUT/$NAME.json")" "$T" "$(hostpath "$OUT/$NAME-window.json")" \
        | tee -a "$OUT/$NAME.txt"

# 원본은 수백 MB라 저장소에 넣을 수 없다. SIGTERM ±5초 창만 증거로 남기고 지운다
echo "  [정리] 원시 시계열 $(du -h "$OUT/$NAME.json" 2>/dev/null | cut -f1) 삭제, 창 추출본 유지"
rm -f "$OUT/$NAME.json"
