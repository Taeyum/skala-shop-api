#!/bin/bash
# 응답 시간 벤치마크 — 같은 머신·같은 DB에서만 비교한다.
#
# 대조군을 반드시 함께 본다. Phase 2에서 BCrypt 도입 후 로그인이 7.6ms → 69.9ms가 됐을 때,
# **BCrypt와 무관한 주문 조회가 움직이지 않았다는 사실**이 증가분의 출처를 확정해줬다.
# 로그인만 재고 "느려졌다"고 하면 원인을 귀속할 수 없다.
#
# 사용법: B=${B:-http://localhost:8080} bash docs/verify/bench.sh
B=http://localhost:8080; U="bn$RANDOM$RANDOM"
REG='{"customerId":"'$U'","customerPassword":"pw1234"}'
J=$(mktemp)
curl -s -X POST $B/api/customers -H 'Content-Type: application/json' -d "$REG" >/dev/null
curl -s -c $J -X POST $B/api/customers/login -H 'Content-Type: application/json' -d "$REG" >/dev/null
avg() { T=0; for i in $(seq 1 30); do T=$(echo "$T + $(curl -s -o /dev/null -w '%{time_total}' "$@")" | bc); done; echo "scale=4; $T/30" | bc; }
echo "  상품목록(인증X) $(avg $B/api/products/list)s"
echo "  주문조회(인증O) $(avg -b $J $B/api/customers/$U)s"
echo "  로그인          $(avg -X POST $B/api/customers/login -H 'Content-Type: application/json' -d "$REG")s"
