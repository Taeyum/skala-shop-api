#!/bin/bash
# ⚠️  적용 범위: phase0-baseline ~ phase1-structure 구간 전용.
#
# 원래 목적 — Phase 1에서 검증 스크립트의 브레이스 확장 결함을 발견한 뒤,
# 이미 "통과"로 기록해둔 5개 커밋을 **되돌아가 다시 검증**하기 위해 만들었다.
# 그래서 그 구간의 커밋들에서 공통으로 성립하는 것만 단언한다.
#
# ★ 현재 코드(phase2-security 이후)에서는 9/18로 실패한다. 결함이 아니라 **계약이 바뀐 것**이다.
#     · Phase 2 B-3 — GET /api/customers/{id}, PUT, DELETE 에 인증을 추가했다.
#       쿠키를 보내지 않는 이 스크립트의 조회는 401이 되어 PARSE_ERR 로 찍힌다 (7건)
#     · Phase 2 B-1 — 음수 포인트 수정이 DATA_NOT_FOUND → ParameterException(400) 으로 바뀌었고,
#       그 앞에 인증 검사가 붙어 NOT_AUTHENTICATED 가 먼저 난다 (1건)
#     · Phase 2 B-2 — Bean Validation 메시지 형식이 바뀌었다 (1건)
#
# 현재 코드의 회귀 검증은 e2e.sh(48건)를 쓴다. 이 파일은 **옛 태그를 재검증할 때만** 쓴다:
#     git checkout phase1-structure && ./gradlew bootRun & ... && bash docs/verify/core-phase0-1.sh
set -u
B=${B:-http://localhost:8080}
C=$(mktemp)
U="core$RANDOM"
fail=0; pass=0

chk() {
  if [ $# -ne 3 ]; then printf "  ⚠️  %-22s 인자 %d개\n" "${1:-?}" "$#"; fail=1; return; fi
  if [ "$2" = "$3" ]; then pass=$((pass+1))
  else printf "  ❌ %-22s 실제=[%s] 기대=[%s]\n" "$1" "$2" "$3"; fail=1; fi
}
j() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null || echo "PARSE_ERR"; }
POST()  { curl -s -X POST "$B$1" -H 'Content-Type: application/json' -d "$2"; }
POSTC() { curl -s -X POST "$B$1" -H 'Content-Type: application/json' -d "$2" -b "$C"; }

REG='{"customerId":"'$U'","customerPassword":"pw1234"}'
DUP='{"customerId":"'$U'","customerPassword":"x"}'
NEG='{"customerId":"'$U'","customerPoint":-1}'

chk "가입 포인트"       "$(POST /api/customers "$REG" | j "d['body']['customerPoint']")" "1000000.0"
L=$(curl -s -X POST "$B/api/customers/login" -H 'Content-Type: application/json' -d "$REG" -c "$C")
chk "로그인"            "$(echo "$L" | j "d['result']")" "success"
chk "쿠키 발급"         "$(grep -c bff-access "$C")" "1"
chk "상품 수"           "$(curl -s "$B/api/products/list?offset=0&count=10" | j "d['body']['total']")" "3"

POSTC /api/customers/order '{"productId":1,"quantity":2}' >/dev/null
O=$(curl -s "$B/api/customers/$U")
chk "주문 후 잔액"      "$(echo "$O" | j "d['body']['customerPoint']")" "970000.0"
chk "수량"              "$(echo "$O" | j "d['body']['products'][0]['quantity']")" "2"
chk "상품명"            "$(echo "$O" | j "d['body']['products'][0]['productName']")" "무선마우스"

POSTC /api/customers/cancel '{"productId":1,"quantity":1}' >/dev/null
chk "취소 후 잔액"      "$(curl -s "$B/api/customers/$U" | j "d['body']['customerPoint']")" "985000.0"

POSTC /api/customers/order '{"productId":1,"quantity":3}' >/dev/null
R=$(curl -s "$B/api/customers/$U")
chk "재주문 행 1개"     "$(echo "$R" | j "len(d['body']['products'])")" "1"
chk "재주문 수량 4"     "$(echo "$R" | j "d['body']['products'][0]['quantity']")" "4"
chk "취소 초과"         "$(POSTC /api/customers/cancel '{"productId":1,"quantity":9}' | j "d['message']")" "INSUFFICIENT_QUANTITY"
POSTC /api/customers/cancel '{"productId":1,"quantity":4}' >/dev/null
chk "전량취소 행삭제"   "$(curl -s "$B/api/customers/$U" | j "len(d['body']['products'])")" "0"
chk "포인트 부족"       "$(POSTC /api/customers/order '{"productId":3,"quantity":100}' | j "d['message']")" "INSUFFICIENT_FUNDS"
chk "미인증"            "$(POST /api/customers/order '{"productId":1,"quantity":1}' | j "d['message']")" "NOT_AUTHENTICATED"
chk "중복 가입"         "$(POST /api/customers "$DUP" | j "d['message']")" "DATA_DUPLICATED"
chk "없는 상품"         "$(curl -s "$B/api/products/999" | j "d['message']")" "DATA_NOT_FOUND"
chk "빈 상품명"         "$(POST /api/products '{"productName":"  ","productPrice":1}' | j "d['message']")" "invalid parameter: productName, productPrice"
chk "음수 포인트 수정"  "$(curl -s -X PUT "$B/api/customers" -H 'Content-Type: application/json' -d "$NEG" | j "d['message']")" "DATA_NOT_FOUND"

rm -f "$C"
[ $fail -eq 0 ] && echo "  통과 $pass/18 ✅" || echo "  통과 $pass/18 ❌"
exit $fail
