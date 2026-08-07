#!/bin/bash
# N+1 측정 — 실행 중인 앱의 SQL 로그로 요청당 쿼리 수를 센다.
#
# 사용법: n1-measure.sh <앱로그경로> <상품수>
#
# 중요 — 쿼리 수를 세기 전에 **반환된 행 수를 먼저 단언**한다.
# 조회가 비어 있거나 401로 튕기면 쿼리가 0~1개라 "개선됐다"로 보인다 (PLAN.md Phase 3 주의 ②).
set -u
LOG=$1
N=${2:-20}
B=${B:-http://localhost:8080}
J=$(mktemp)   # 쿠키

fail() { echo "  ✗ $*"; exit 1; }

# 로그의 현재 SQL 라인 수
sqlcount() { grep -cE 'o\.h\.SQL|org\.hibernate\.SQL' "$LOG" 2>/dev/null || echo 0; }

# ── 픽스처: 고객 1명 + 서로 다른 상품 N개 + 각 1개씩 주문 ──────────────
U="n1u$RANDOM$RANDOM"
TAG="n1p$RANDOM$RANDOM"
BODY='{"customerId":"'$U'","customerPassword":"pw1234"}'
curl -s -X POST "$B/api/customers" -H 'Content-Type: application/json' -d "$BODY" >/dev/null
curl -s -c "$J" -X POST "$B/api/customers/login" -H 'Content-Type: application/json' -d "$BODY" >/dev/null

PIDS=()
for i in $(seq 1 "$N"); do
  P='{"productName":"'$TAG-$i'","productPrice":1000}'
  R=$(curl -s -X POST "$B/api/products" -H 'Content-Type: application/json' -d "$P")
  ID=$(echo "$R" | python3 -c 'import sys,json;print(json.load(sys.stdin)["body"]["id"])' 2>/dev/null)
  [ -z "$ID" ] && fail "상품 생성 실패: $R"
  PIDS+=("$ID")
done
# 서로 다른 상품 N개를 주문한다 — 같은 상품 반복은 1차 캐시 때문에 N+1이 재현되지 않는다
for ID in "${PIDS[@]}"; do
  O='{"productId":'$ID',"quantity":1}'
  curl -s -b "$J" -X POST "$B/api/customers/order" -H 'Content-Type: application/json' -d "$O" >/dev/null
done

# ── 측정 ① 고객 주문 조회 (N+1 대상) ─────────────────────────────────
sleep 0.7                      # 로그 flush 대기
BEFORE=$(sqlcount)
RESP=$(curl -s -b "$J" "$B/api/customers/$U")
sleep 0.7
AFTER=$(sqlcount)
Q=$((AFTER-BEFORE))

# ★ 쿼리 수보다 먼저 — 조회가 실제로 일했는지 단언한다
ROWS=$(echo "$RESP" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(len(d["body"]["products"]))' 2>/dev/null)
[ -z "$ROWS" ] && fail "응답 파싱 실패 (401로 튕겼을 수 있다): $RESP"
[ "$ROWS" -ne "$N" ] && fail "반환 행 수 $ROWS ≠ 기대 $N — 측정 대상이 일하지 않았다. 쿼리 수는 의미 없다"
echo "  ✓ 반환 행 수 $ROWS 건 (= 주문한 상품 수)"
echo "  GET /api/customers/{id}   쿼리 $Q 개   [상품 $N 종]"

# ── 측정 ② 대조군: 상품 목록 (N+1과 무관해야 한다) ────────────────────
BEFORE=$(sqlcount)
CTL=$(curl -s "$B/api/products/list?offset=0&count=10")
sleep 0.7
AFTER=$(sqlcount)
CQ=$((AFTER-BEFORE))
CROWS=$(echo "$CTL" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["body"]["list"]))' 2>/dev/null)
[ -z "$CROWS" ] && fail "대조군 응답 파싱 실패: $CTL"
[ "$CROWS" -eq 0 ] && fail "대조군 반환 0건 — 대조군도 측정되지 않았다"
echo "  ✓ 대조군 반환 행 수 $CROWS 건"
echo "  GET /api/products/list    쿼리 $CQ 개   (대조군 — 변하지 않아야 한다)"

echo "MEASURED $N $Q $CQ"
rm -f "$J"
