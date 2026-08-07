#!/bin/bash
# SPEC.md 5절 E2E 6단계 + 비즈니스 규칙 회귀
# SPEC은 skala01을 쓰지만, 앱 재기동 없이 반복 실행하려고 매 회 고유 ID를 쓴다.
#
# 주의 — JSON 페이로드를 "$( ... "{...}" ... )" 형태로 중첩해 넣지 말 것.
# 중첩 따옴표가 풀리면서 bash 중괄호 확장이 걸려 {"a":1,"b":2}가 두 단어로 쪼개진다.
# 그러면 chk가 인자를 4개 받아 $2와 $3이 둘 다 PARSE_ERR이 되어 '통과'로 잘못 판정된다.
# 반드시 페이로드를 변수에 먼저 담고 "$VAR"로 넘긴다.
set -u
B=${B:-http://localhost:8080}
C=$(mktemp)
U="skala$RANDOM"
fail=0
pass=0

chk() { # chk "라벨" 실제 기대
  if [ $# -ne 3 ]; then printf "  ⚠️  %-24s 인자 %d개 (3개여야 함)\n" "$1" "$#"; fail=1; return; fi
  if [ "$2" = "$3" ]; then printf "  ✅ %-24s %s\n" "$1" "$2"; pass=$((pass+1))
  else printf "  ❌ %-24s 실제=[%s] 기대=[%s]\n" "$1" "$2" "$3"; fail=1; fi
}
j() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null || echo "PARSE_ERR"; }
POST() { curl -s -X POST "$B$1" -H 'Content-Type: application/json' -d "$2"; }
POSTC() { curl -s -X POST "$B$1" -H 'Content-Type: application/json' -d "$2" -b "$C"; }
ST()  { curl -s -o /dev/null -w '%{http_code}' "$@"; }

REG='{"customerId":"'$U'","customerPassword":"pw1234"}'
BADPW='{"customerId":"'$U'","customerPassword":"wrongpw"}'
DUP='{"customerId":"'$U'","customerPassword":"x"}'
NEG='{"customerId":"'$U'","customerPoint":-1}'
MASS='{"customerId":"m'$U'","customerPassword":"pw","customerPoint":999999999}'

echo "── E2E 6단계 ──"
R=$(POST /api/customers "$REG")
chk "1) 가입 포인트" "$(echo "$R" | j "d['body']['customerPoint']")" "1000000.0"

LH=$(curl -s -i -X POST "$B/api/customers/login" -H 'Content-Type: application/json' -d "$REG" -c "$C")
L=$(echo "$LH" | tail -1)
chk "2) 로그인" "$(echo "$L" | j "d['result']")" "success"
# 값이 null인지가 아니라 '키가 아예 없는지'를 본다 — DTO 분리로 필드 자체가 사라졌다
chk "   비밀번호 키 부재" "$(echo "$L" | j "'customerPassword' in d['body']")" "False"
chk "   대리키 id 부재" "$(echo "$L" | j "'id' in d['body']")" "False"
chk "   쿠키 발급" "$(grep -c bff-access "$C")" "1"
# 쿠키 보안 플래그 — 헤더 원문에서 직접 확인한다
SC=$(echo "$LH" | grep -i "^set-cookie" | tr -d '\r')
chk "   HttpOnly" "$(echo "$SC" | grep -ci "HttpOnly")" "1"
chk "   SameSite=Strict" "$(echo "$SC" | grep -ci "SameSite=Strict")" "1"
chk "   Path=/" "$(echo "$SC" | grep -ci "Path=/")" "1"

chk "3) 상품 수" "$(curl -s "$B/api/products/list?offset=0&count=10" | j "d['body']['total']")" "3"

POSTC /api/customers/order '{"productId":1,"quantity":2}' >/dev/null
O=$(curl -s -b "$C" "$B/api/customers/$U")
chk "4) 주문 후 잔액" "$(echo "$O" | j "d['body']['customerPoint']")" "970000.0"
chk "5) 수량" "$(echo "$O" | j "d['body']['products'][0]['quantity']")" "2"
chk "   상품명" "$(echo "$O" | j "d['body']['products'][0]['productName']")" "무선마우스"

POSTC /api/customers/cancel '{"productId":1,"quantity":1}' >/dev/null
chk "6) 취소 후 잔액" "$(curl -s -b "$C" "$B/api/customers/$U" | j "d['body']['customerPoint']")" "985000.0"

echo "── 비즈니스 규칙 회귀 ──"
POSTC /api/customers/order '{"productId":1,"quantity":3}' >/dev/null
R2=$(curl -s -b "$C" "$B/api/customers/$U")
chk "재주문 누적(행 1개)" "$(echo "$R2" | j "len(d['body']['products'])")" "1"
chk "재주문 누적(수량 4)" "$(echo "$R2" | j "d['body']['products'][0]['quantity']")" "4"

X=$(POSTC /api/customers/cancel '{"productId":1,"quantity":9}')
chk "취소 초과" "$(echo "$X" | j "d['message']")" "INSUFFICIENT_QUANTITY"
POSTC /api/customers/cancel '{"productId":1,"quantity":4}' >/dev/null
chk "전량취소 행삭제" "$(curl -s -b "$C" "$B/api/customers/$U" | j "len(d['body']['products'])")" "0"

X=$(POSTC /api/customers/order '{"productId":3,"quantity":100}')
chk "포인트 부족" "$(echo "$X" | j "d['message']")" "INSUFFICIENT_FUNDS"
X=$(POST /api/customers/order '{"productId":1,"quantity":1}')
chk "미인증" "$(echo "$X" | j "d['message']")" "NOT_AUTHENTICATED"
X=$(POST /api/customers "$DUP")
chk "중복 가입" "$(echo "$X" | j "d['message']")" "DATA_DUPLICATED"
chk "없는 상품" "$(curl -s "$B/api/products/999" | j "d['message']")" "DATA_NOT_FOUND"
X=$(POST /api/products '{"productName":"  ","productPrice":1}')
chk "빈 상품명" "$(echo "$X" | j "d['message']")" "invalid parameter: productName"
X=$(curl -s -X PUT "$B/api/customers" -H 'Content-Type: application/json' -b "$C" -d "$NEG")
chk "음수 포인트 수정" "$(echo "$X" | j "d['message']")" "invalid parameter: customerPoint"
X=$(POST /api/customers/login "$BADPW")
chk "틀린 비밀번호 거부" "$(echo "$X" | j "d['message']")" "NOT_AUTHENTICATED"
X=$(POST /api/customers "$MASS")
chk "Mass Assignment 차단" "$(echo "$X" | j "d['body']['customerPoint']")" "1000000.0"

echo "── 수량 검증 ──"
BAL0=$(curl -s -b "$C" "$B/api/customers/$U" | j "d['body']['customerPoint']")
chk "음수 수량 주문 거부" "$(POSTC /api/customers/order '{"productId":1,"quantity":-5}' | j "d['message']")" "invalid parameter: quantity"
chk "0 수량 주문 거부" "$(POSTC /api/customers/order '{"productId":1,"quantity":0}' | j "d['message']")" "invalid parameter: quantity"
chk "음수 수량 취소 거부" "$(POSTC /api/customers/cancel '{"productId":1,"quantity":-10}' | j "d['message']")" "invalid parameter: quantity"
chk "잔액 변동 없음" "$(curl -s -b "$C" "$B/api/customers/$U" | j "d['body']['customerPoint']")" "$BAL0"
chk "여러 필드 위반 형식" "$(POST /api/products '{"productName":"","productPrice":-1}' | j "d['message']")" "invalid parameter: productName, productPrice"

echo "── 참조 무결성 ──"
RI="ri$RANDOM"
RIREG='{"customerId":"'$RI'","customerPassword":"pw1234"}'
RIDEL='{"customerId":"'$RI'"}'
RC=$(mktemp)
POST /api/customers "$RIREG" >/dev/null
curl -s -X POST "$B/api/customers/login" -H 'Content-Type: application/json' -d "$RIREG" -c "$RC" >/dev/null
curl -s -X POST "$B/api/customers/order" -H 'Content-Type: application/json' -b "$RC" -d '{"productId":2,"quantity":1}' >/dev/null
chk "주문 있는 상품 삭제 → 409" "$(ST -X DELETE "$B/api/products" -H 'Content-Type: application/json' -d '{"id":2}')" "409"
chk "보유 상품 있는 탈퇴 → 409" "$(ST -X DELETE "$B/api/customers" -H 'Content-Type: application/json' -b "$RC" -d "$RIDEL")" "409"
curl -s -X POST "$B/api/customers/cancel" -H 'Content-Type: application/json' -b "$RC" -d '{"productId":2,"quantity":1}' >/dev/null
chk "취소 후 탈퇴 → 200" "$(ST -X DELETE "$B/api/customers" -H 'Content-Type: application/json' -b "$RC" -d "$RIDEL")" "200"
rm -f "$RC"

echo "── BOLA 방어 ──"
V="victim$RANDOM"
VREG='{"customerId":"'$V'","customerPassword":"pw1234"}'
VUPD='{"customerId":"'$V'","customerPoint":9999999}'
VDEL='{"customerId":"'$V'"}'
NOACCT='{"customerId":"nosuch-zzz","customerPoint":1}'
POST /api/customers "$VREG" >/dev/null
chk "남의 계정 수정 → 403" "$(ST -X PUT "$B/api/customers" -H 'Content-Type: application/json' -b "$C" -d "$VUPD")" "403"
chk "남의 계정 삭제 → 403" "$(ST -X DELETE "$B/api/customers" -H 'Content-Type: application/json' -b "$C" -d "$VDEL")" "403"
chk "남의 주문 조회 → 403" "$(ST -b "$C" "$B/api/customers/$V")" "403"
chk "본인 주문 조회 → 200" "$(ST -b "$C" "$B/api/customers/$U")" "200"
chk "인증 없이 조회 → 401" "$(ST "$B/api/customers/$U")" "401"
chk "없는 계정도 403 (존재 미노출)" "$(ST -X PUT "$B/api/customers" -H 'Content-Type: application/json' -b "$C" -d "$NOACCT")" "403"
chk "BOLA 후 피해자 잔액 온전" "$(POST /api/customers/login "$VREG" | j "d['body']['customerPoint']")" "1000000.0"

echo "── HTTP 상태 매핑 ──"
chk "없는 상품 → 404" "$(ST "$B/api/products/999")" "404"
chk "중복 가입 → 409" "$(ST -X POST "$B/api/customers" -H 'Content-Type: application/json' -d "$DUP")" "409"
chk "미인증 → 401" "$(ST -X POST "$B/api/customers/order" -H 'Content-Type: application/json' -d '{"productId":1,"quantity":1}')" "401"
chk "필수값 누락 → 400" "$(ST -X POST "$B/api/products" -H 'Content-Type: application/json' -d '{"productName":"  ","productPrice":1}')" "400"
chk "깨진 JSON → 400" "$(ST -X POST "$B/api/customers" -H 'Content-Type: application/json' -d '{"broken"')" "400"
chk "정상 조회 → 200" "$(ST "$B/api/products/1")" "200"

echo "── 사용자 열거 방어 ──"
NOUSER='{"customerId":"nosuchuser-zzz","customerPassword":"whatever"}'
S1=$(ST -X POST "$B/api/customers/login" -H 'Content-Type: application/json' -d "$BADPW")
S2=$(ST -X POST "$B/api/customers/login" -H 'Content-Type: application/json' -d "$NOUSER")
B1=$(POST /api/customers/login "$BADPW" | j "d['message']")
B2=$(POST /api/customers/login "$NOUSER" | j "d['message']")
chk "없는 ID·틀린 PW 상태 동일" "$S1/$S2" "401/401"
chk "없는 ID·틀린 PW 바디 동일" "$B1/$B2" "NOT_AUTHENTICATED/NOT_AUTHENTICATED"

rm -f "$C"
echo
echo "통과 $pass 건"
[ $fail -eq 0 ] && echo "═══ 전체 통과 ═══" || echo "═══ 실패 있음 ═══"
exit $fail
