#!/bin/bash
# 측정 결과 소급 검증 — **하네스나 파싱 로직을 고쳤을 때** 과거 측정 결과를 다시 확인한다.
#
#   bash docs/verify/retro-check.sh
#
# 앱도 DB도 필요 없다. `docs/evidence/perf/` 의 파일만 읽는다.
#
# ⚠️ **측정이 끝난 뒤에 실행한다.** 진행 중인 스윕의 요약 파일은 헤더만 있고 결과 줄이 없어
#    ③ 에서 걸린다. 이건 오탐이 아니라 정상 동작이다 — "결과 줄이 없는 요약 파일"은
#    측정이 중간에 죽은 경우와 구분되지 않으므로, 규칙을 느슨하게 하는 대신 실행 시점을 지킨다.
#
# ★ 왜 필요한가 — **도구를 고치면 그 도구로 낸 과거 결과의 신뢰도가 함께 흔들린다.**
#   Phase 1에서 검증 스크립트의 거짓 통과를 고친 뒤 커밋 5개를 되돌아가 18/18을
#   다시 돌린 것과 같은 절차다. 그때도 결과는 "과거의 통과는 실제로도 통과였다"였고,
#   값은 결과가 아니라 **확인했다는 사실** 자체에 있었다.
#
# ★ 이 도구의 핵심은 ④ JSON 독립 검산이다.
#   ①~③은 **같은 텍스트를 같은 grep 으로 다시 보는 것**이라, 그 grep 이 틀렸다면
#   과거 파일에서도 똑같이 틀린다 — 자기 자신을 근거로 삼는 셈이다.
#   JSON 요약은 k6 가 별도 경로로 내보낸 산출물이므로 독립적인 검산이 된다.
#
# ★ 대조군을 반드시 포함한다.
#   실패한 측정 파일(`*FAILED*`)을 함께 검사해 **실제로 걸리는지** 본다.
#   이것이 없으면 "전부 ✅"는 **"검사기가 아무것도 잡지 못한다"** 와 구분되지 않는다.
#   대조군이 하나도 없거나 걸리지 않으면 이 스크립트는 실패로 끝난다.
set -u
SELF_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$SELF_DIR/pyguard.sh"                      # ④ 에서 파이썬을 쓴다
ROOT=$(cd "$SELF_DIR/../.." && pwd)
E="$ROOT/docs/evidence/perf"

fail=0; pass=0; caught=0; controls=0
ok()   { printf "  ✅ %s\n" "$*"; pass=$((pass+1)); }
bad()  { printf "  ❌ %s\n" "$*"; fail=1; }

# 요약표인지 원시 출력인지 구분한다.
# ★ 이전 판은 글롭 `sweep-*-*.txt` 로 원시 출력을 골랐는데,
#   라벨에 하이픈이 있으면(win-repeat) **요약 파일까지 걸려 오탐이 났다.**
#   파일명이 아니라 **내용**으로 판별한다 — 요약표에는 k6 지표 줄이 없다.
is_raw() { grep -qE '^\s+(http_reqs|iterations)' "$1"; }

echo "═══ ① sweep 원시 출력 — 파싱 실패 검사 (sweep.sh 의 가드와 같은 패턴) ═══"
for f in "$E"/sweep-*.txt; do
  [ -e "$f" ] || continue
  b=$(basename "$f")
  case "$b" in *FAILED*) continue;; esac          # 대조군은 ⑤ 에서 따로 본다
  if ! is_raw "$f"; then printf "  · %-34s 요약표 — 원시 검사 대상 아님\n" "$b"; continue; fi
  RAW=$(cat "$f")
  P95=$(echo "$RAW" | grep -oE "p\(95\)=[0-9.]+m?s" | head -1)
  RPS=$(echo "$RAW" | grep -E "^\s+http_reqs" | grep -oE "[0-9.]+/s")
  OK=$(echo "$RAW"  | grep -E "checks_succeeded" | grep -oE "[0-9.]+%")
  if [ -z "$P95" ] || [ -z "$OK" ] || [ -z "$RPS" ]; then
    bad "$b  p95=[$P95] ok=[$OK] rps=[$RPS]"
  else
    printf "  ✅ %-34s p95=%-12s ok=%-9s rps=%s\n" "$b" "$P95" "$OK" "$RPS"; pass=$((pass+1))
  fi
done

echo
echo "═══ ② run.sh 산출물 — http_reqs 존재 검사 (run.sh 의 가드와 같은 패턴) ═══"
for f in "$E"/*-load.txt "$E"/*-stress.txt "$E"/*-spike.txt "$E"/*-integrity.txt; do
  [ -e "$f" ] || continue
  b=$(basename "$f")
  if grep -qE '^\s+http_reqs' "$f"; then
    ok "$(printf '%-24s %s' "$b" "$(grep -E '^\s+http_reqs' "$f" | head -1 | tr -s ' ')")"
  else
    bad "$b — http_reqs 없음"
  fi
done

echo
echo "═══ ③ 요약표에 빈 칸이 있는가 (파싱 실패의 육안 증상) ═══"
for f in "$E"/sweep-*.txt; do
  [ -e "$f" ] || continue
  b=$(basename "$f")
  case "$b" in *FAILED*) continue;; esac
  is_raw "$f" && continue
  n=$(grep -cE "^[0-9]+ " "$f" || true)
  blank=$(grep -E "^[0-9]+ " "$f" | grep -cE "성공[[:space:]]+p95|p95[[:space:]]+[0-9.]+/s" || true)
  if [ "$n" -eq 0 ]; then bad "$b — 결과 줄이 하나도 없다"
  elif [ "$blank" -gt 0 ]; then bad "$b — 빈 칸 있는 줄 $blank/$n"
  else ok "$(printf '%-34s %s 줄 전부 값이 채워져 있음' "$b" "$n")"; fi
done

echo
echo "═══ ④ ★ JSON 독립 검산 — k6 가 별도 경로로 내보낸 산출물과 대조 ═══"
for f in "$E"/*.json; do
  [ -e "$f" ] || continue
  OUT=$($PY - "$f" <<'PY'
import json, os, sys
p = sys.argv[1]
try:
    d = json.load(open(p, encoding='utf-8'))
except Exception as ex:
    print(f"FAIL {os.path.basename(p)} 파싱 불가: {ex}"); raise SystemExit(0)
m = d.get('metrics', {})
cnt = m.get('http_reqs', {}).get('count')
p95 = m.get('http_req_duration', {}).get('p(95)')
fr  = m.get('http_req_failed', {})
fr  = fr.get('rate', fr.get('value'))
tag = "OK" if cnt else "FAIL"
print(f"{tag} {os.path.basename(p):24s} http_reqs={cnt}  p95={p95}  실패율={fr}")
PY
)
  case "$OUT" in
    OK*)   ok "${OUT#OK }" ;;
    *)     bad "${OUT#FAIL }" ;;
  esac
done

echo
echo "═══ ⑤ 대조군 — 실패한 측정이 실제로 걸리는가 ═══"
for f in "$E"/*FAILED*.txt; do
  [ -e "$f" ] || continue
  controls=$((controls+1))
  RAW=$(cat "$f")
  P95=$(echo "$RAW" | grep -oE "p\(95\)=[0-9.]+m?s" | head -1)
  OK2=$(echo "$RAW" | grep -E "checks_succeeded" | grep -oE "[0-9.]+%")
  RPS=$(echo "$RAW" | grep -E "^\s+http_reqs" | grep -oE "[0-9.]+/s")
  if [ -z "$P95" ] || [ -z "$OK2" ] || [ -z "$RPS" ]; then
    ok "$(printf '%-34s 걸림 (기대한 동작)' "$(basename "$f")")"; caught=$((caught+1))
  else
    bad "$(basename "$f") — 실패한 측정인데 가드를 통과했다. **검사기가 고장났다**"
  fi
done
if [ "$controls" -eq 0 ]; then
  bad "대조군 파일이 하나도 없다 — '전부 통과'가 '아무것도 잡지 못한다'와 구분되지 않는다"
fi

echo
echo "───────────────────────────────────────────────"
echo "  통과 $pass 건 · 대조군 $caught/$controls 건 걸림"
if [ "$fail" -eq 0 ]; then
  echo "  ✅ 과거 측정에서 파싱 실패 흔적 없음 — 그리고 검사기가 실패할 수 있음을 확인했다"
else
  echo "  ❌ 확인 필요 (위 항목)"
fi
exit "$fail"
