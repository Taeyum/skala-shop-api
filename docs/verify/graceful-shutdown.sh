#!/bin/bash
# graceful shutdown 효과 측정 — 지속 부하 중 SIGTERM을 보내고 실패율을 비교한다.
#
# ★ Phase 5에서 이 측정은 **실패했다.** 세 가지 실험 모두 graceful과 immediate를
#   변별하지 못했고, 마지막은 curl 프로세스 기동이 SIGTERM보다 느려 측정 자체가 성립하지 않았다
#   (60건 전부 status 000). 확인된 것은 로그뿐이다 —
#   "Commencing graceful shutdown. Waiting for active requests to complete"가
#   graceful 에서만 나온다.
#
#   원인 가설: 이 API의 요청이 3~5ms로 끝나 "진행 중"인 창이 측정 도구의 오버헤드보다 좁다.
#   Phase 6에서 k6로 재시도한다 — curl보다 훨씬 조밀하게 요청을 보내므로 창을 잡을 확률이 높다.
#   그래도 변별되지 않으면 **인위적으로 느린 엔드포인트를 임시로 만들어** 가설을 확인하고,
#   그 임시 코드는 측정 후 반드시 제거한다.
#
# ── [2026-08-08 후속] 여기까지가 당시의 계획이고, 실제로는 이렇게 됐다 ──────────
#
#   ★ 이 스크립트는 더 이상 쓰지 않는다. 대체: docs/perf/shutdown.sh (k6 기반)
#     이 판은 application.yml 을 정규식으로 재작성한다 — **측정이 운영 소스를 고치는 구조**라
#     Phase 4의 변이 사고와 같은 위험이 있다. 새 판은 SERVER_SHUTDOWN 환경변수를 쓴다.
#
#   k6 재시도 결과 — 창은 잡혔고, 효과는 변별되지 않았다
#     · SIGTERM 시점 인플라이트 19~20건 (curl 판은 0건이라 측정 자체가 무효였다)
#     · graceful 종료 창의 실제 길이 **66ms** (Commencing → complete)
#     · 그래도 graceful/immediate 가 변별되지 않는다. 보호 대상(20건)이
#       실행 간 편차(약 300건)보다 한 자릿수 작기 때문이다
#
#   ★ "인위적으로 느린 엔드포인트"는 **실행하지 않기로 했다.** 위 계획을 지우지 않고 남긴다.
#     · 그것이 증명하는 것은 "긴 요청이 있으면 보호된다"이지 "이 API에서 값어치가 있다"가 아니다.
#       전자는 graceful shutdown 의 정의를 다시 말하는 것에 가깝고, 알고 싶었던 것은 후자다.
#     · 제출이 걸린 시점에 운영 코드에 임시 코드를 넣는 위험이 이득보다 크다.
#       Phase 4에서 변이 스크립트가 강제 종료로 원복되지 않아 운영 코드에 변이를 남긴 사고가 있었다.
#
#   결론은 "측정할 수 없다"가 아니라 **"이 시스템에서는 효과가 관측될 조건이 형성되지 않는다"** 다.
#   상세는 DECISIONS.md 28절.
#
#   "설정이 켜졌다"와 "효과가 있다"는 다르다. 후자를 증명하지 못했으면 그렇게 적는다.
#
# 사용법: bash docs/verify/graceful-shutdown.sh <graceful|immediate>
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/pyguard.sh"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODE=${1:-graceful}
B=${B:-http://localhost:8080}
OUT=$(mktemp)

# ★ 이 블록은 application.yml 을 **덮어쓴다.** 인터프리터가 스텁이면
#   아무 일도 없이 통과해 "immediate 로 바꿨다"고 착각한 채 측정하게 된다.
#   위 pyguard 가 그 경우를 먼저 막는다.
$PY - "$MODE" <<'PY'
import re, sys, os
root = os.environ.get('ROOT') or os.getcwd()
path = os.path.join(root, 'src/main/resources/application.yml')
s = open(path).read()
open(path, 'w').write(re.sub(r'  shutdown: \w+', '  shutdown: ' + sys.argv[1], s))
PY

restore() {
  (cd "$ROOT" && git checkout -- src/main/resources/application.yml)
}
trap restore EXIT INT TERM

(cd "$ROOT" && docker compose up -d --build app >/dev/null 2>&1)
for i in $(seq 1 60); do
  [ "$(docker inspect --format '{{.State.Health.Status}}' \
      "$(cd "$ROOT" && docker compose ps -q app)" 2>/dev/null)" = "healthy" ] && break
  sleep 2
done

# 4초 동안 8개 워커가 쉬지 않고 요청한다. 종료 순간 실제로 처리 중인 요청이 있어야 변별력이 생긴다
for w in $(seq 1 8); do
  ( END=$((SECONDS+4)); while [ $SECONDS -lt $END ]; do
      curl -s -m 25 -o /dev/null -w '%{http_code}\n' "$B/api/products/list" >> "$OUT"
    done ) &
done
sleep 2
kill -TERM "$(lsof -nP -iTCP:8080 -sTCP:LISTEN -t 2>/dev/null)" 2>/dev/null
wait

echo "  [$MODE] 응답 분포: $(sort "$OUT" | uniq -c | tr '\n' ' ')"
echo "  [$MODE] graceful 로그: $( (cd "$ROOT" && docker compose logs app 2>/dev/null) \
        | grep -ci 'Commencing graceful shutdown') 건"
rm -f "$OUT"
