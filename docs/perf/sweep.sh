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

# Windows(Git Bash) 대응 — 근거는 run.sh 의 같은 블록 주석 참조.
# 요약: MSYS가 컨테이너 안 경로(/perf)까지 Windows 경로로 바꿔 k6가 스크립트를 못 찾는다.
export MSYS_NO_PATHCONV=1
hostpath() {
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) cygpath -m "$1" ;;
    *) printf '%s' "$1" ;;
  esac
}

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
  RAW=$(docker run --rm --network "$NET" -v "$(hostpath "$ROOT/docs/perf"):/perf:ro" \
        -e BASE=http://app:8080 -e VUS=50 -e DURATION=45s \
        grafana/k6:latest run /perf/load.js 2>&1)
  echo "$RAW" > "$OUT/sweep-$LABEL-$VALUE.txt"
  P95=$(echo "$RAW" | grep -oE "p\(95\)=[0-9.]+m?s" | head -1)
  RPS=$(echo "$RAW" | grep -E "^\s+http_reqs" | grep -oE "[0-9.]+/s")
  OK=$(echo "$RAW"  | grep -E "checks_succeeded" | grep -oE "[0-9.]+%")
  ITER=$(echo "$RAW"| grep -E "^\s+iterations" | grep -oE "[0-9.]+/s")

  # ★ 파싱이 비면 즉시 중단한다.
  # 이전 판은 빈 값을 그대로 표에 적었다 — "10  성공   p95   iter" 같은 줄이 3개 남았고
  # 목록만 보면 측정이 끝난 것처럼 보인다. **빈 값을 기록하면 '측정했다'가 된다.**
  # (2026-08-08 실측: MSYS 경로 변환으로 k6가 스크립트를 못 찾았는데 exit 0 으로 끝났다)
  if [ -z "$P95" ] || [ -z "$OK" ] || [ -z "$RPS" ]; then
    {
      echo "  ❌ k6 출력에서 지표를 뽑지 못했다 ($ENVVAR=$VALUE) — 측정이 성립하지 않았다."
      echo "     원시 출력: $OUT/sweep-$LABEL-$VALUE.txt"
    } | tee -a "$RESULT" >&2
    echo "$RAW" | tail -5 >&2
    exit 1
  fi

  printf "%-8s 성공 %-8s p95 %-10s %-14s iter %s\n" "$VALUE" "$OK" "$P95" "$RPS" "$ITER" | tee -a "$RESULT"
done
echo; echo "결과: $RESULT"
