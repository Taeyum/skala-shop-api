#!/bin/bash
# 설정값을 바꿔가며 같은 시나리오를 반복해 **곡선**을 그린다.
# 한 점만 재고 "좋아졌다"고 하지 않는다 — 어느 방향으로 가는지가 근거다.
#
# 사용법: sweep.sh <라벨> <환경변수이름> <값1> <값2> ...
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LABEL=$1; ENVVAR=$2; shift 2
OUT="$ROOT/docs/evidence/perf"; mkdir -p "$OUT"
RESULT="$OUT/sweep-$LABEL.txt"

{
  echo "# ===== 스윕 측정 조건 ====="
  echo "# 일시     : $(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "# 커밋     : $(cd "$ROOT" && git rev-parse HEAD)"
  echo "# 변수     : $ENVVAR = $*"
  echo "# 시나리오 : load.js VUS=50 DURATION=45s (워밍업 20초 별도)"
  echo "# 각 값마다 app 재기동 → DB 초기화 → 측정"
  echo "# =========================="
} > "$RESULT"

restore() {
  (cd "$ROOT" && docker compose up -d --force-recreate app >/dev/null 2>&1)
}
trap restore EXIT INT TERM

for VALUE in "$@"; do
  echo "  ▶ $ENVVAR=$VALUE"
  # 환경변수를 주입해 앱만 재생성한다. compose의 environment는 그대로 두고 override
  (cd "$ROOT" && env "$ENVVAR=$VALUE" docker compose up -d --force-recreate app >/dev/null 2>&1)
  for i in $(seq 1 60); do
    [ "$(docker inspect --format '{{.State.Health.Status}}' \
        "$(cd "$ROOT" && docker compose ps -q app)" 2>/dev/null)" = "healthy" ] && break
    sleep 2
  done
  NET=$(docker inspect "$(cd "$ROOT" && docker compose ps -q app)" \
          --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}')
  RAW=$(docker run --rm --network "$NET" -v "$ROOT/docs/perf:/perf:ro" \
        -e BASE=http://app:8080 -e VUS=50 -e DURATION=45s \
        grafana/k6:latest run /perf/load.js 2>&1)
  echo "$RAW" > "$OUT/sweep-$LABEL-$VALUE.txt"
  P95=$(echo "$RAW" | grep -oE "p\(95\)=[0-9.]+m?s" | head -1)
  RPS=$(echo "$RAW" | grep -E "^\s+http_reqs" | grep -oE "[0-9.]+/s")
  OK=$(echo "$RAW"  | grep -E "checks_succeeded" | grep -oE "[0-9.]+%")
  ITER=$(echo "$RAW"| grep -E "^\s+iterations" | grep -oE "[0-9.]+/s")
  printf "%-8s 성공 %-8s p95 %-10s %-14s iter %s\n" "$VALUE" "$OK" "$P95" "$RPS" "$ITER" | tee -a "$RESULT"
done
echo; echo "결과: $RESULT"
