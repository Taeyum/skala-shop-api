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
