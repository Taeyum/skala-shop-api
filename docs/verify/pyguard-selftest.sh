#!/bin/bash
# pyguard.sh 역검증 — "방어가 실제로 발동하는지"를 확인한다.
#
#   bash docs/verify/pyguard-selftest.sh
#
# 앱도 DB도 필요 없다. 파이썬 인터프리터 해석만 시험한다.
#
# ★ 왜 이 파일이 있는가 — 방어를 넣어놓고 발동하는 것을 안 보면
#   그 방어는 "있다고 믿는 것"이지 있는 것이 아니다.
#   이 저장소의 변이 테스트(mutate.py)와 같은 발상이고, 대상이 테스트가 아니라 가드일 뿐이다.
#
# 모사하는 것 — Windows의 Microsoft Store 앱 실행 별칭(스텁):
#   인자를 무시하고 `Python ` 한 줄만 찍은 뒤 exit 49. **대상 스크립트를 실행하지 않는다.**
set -u
SELF_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd "$SELF_DIR/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT INT TERM     # 가짜 python3 이 남으면 그 자체가 함정이 된다

fail=0; pass=0
chk() {   # chk "라벨" 실제 기대
  if [ $# -ne 3 ]; then printf "  ⚠️  %-34s 인자 %d개 (3개여야 함)\n" "${1:-?}" "$#"; fail=1; return; fi
  if [ "$2" = "$3" ]; then printf "  ✅ %-34s %s\n" "$1" "$2"; pass=$((pass+1))
  else printf "  ❌ %-34s 실제=[%s] 기대=[%s]\n" "$1" "$2" "$3"; fail=1; fi
}

# 스텁 모사본. 전부 스텁인 디렉터리와, python3 만 스텁인 디렉터리를 나눠 만든다
mkdir -p "$TMP/allstub" "$TMP/stub3"
for n in python3 python py; do
  printf '#!/bin/bash\nprintf "Python "\nexit 49\n' > "$TMP/allstub/$n"; chmod +x "$TMP/allstub/$n"
done
cp "$TMP/allstub/python3" "$TMP/stub3/python3"

BARE="$TMP/allstub:/usr/bin:/bin"

echo "── 모사본이 진짜 스텁과 같은 동작인지 먼저 확인 ──────────────"
OUT=$("$TMP/allstub/python3" -c 'print(1)' 2>&1); RC=$?
chk "스텁 모사: 출력" "$OUT" "Python "
chk "스텁 모사: 종료 코드" "$RC" "49"

echo "── A~F ────────────────────────────────────────────────────"

# A. 모든 후보가 스텁이면 시끄럽게 실패한다
RC=0; OUT=$(PATH="$BARE" bash "$SELF_DIR/pyguard.sh" "$SELF_DIR/mutate.py" --list 2>&1) || RC=$?
chk "A 전부 스텁 → 종료 코드" "$RC" "1"
case "$OUT" in *"동작하는 python3 을 찾지 못했다"*) R=있음;; *) R=없음;; esac
chk "A 전부 스텁 → 안내 메시지" "$R" "있음"

# B. python3 만 스텁이면 살아 있는 인터프리터로 폴백한다
RC=0; OUT=$(PATH="$TMP/stub3:$PATH" bash "$SELF_DIR/pyguard.sh" -c 'print("ALIVE")' 2>/dev/null) || RC=$?
chk "B python3만 스텁 → 폴백 실행" "$OUT" "ALIVE"
chk "B python3만 스텁 → 종료 코드" "$RC" "0"

# C. e2e.sh 는 검사를 **시작하기 전에** 멈춘다 (앱이 떠 있든 아니든)
RC=0; OUT=$(PATH="$BARE" bash "$SELF_DIR/e2e.sh" 2>&1) || RC=$?
chk "C e2e.sh → 종료 코드" "$RC" "1"
chk "C e2e.sh → 통과 표시 0건" "$(printf '%s' "$OUT" | grep -c '✅')" "0"

# D. 요구 버전 미달이면 아무 일도 하지 않고 exit 2
#    (설치된 파이썬이 하나뿐이라 사본에서 요구 버전을 올려 시험한다)
sed 's/^_MIN = (3, 8)/_MIN = (99, 0)/' "$SELF_DIR/mutate.py" > "$TMP/mutate_oldpy.py"
RC=0; OUT=$(python3 "$TMP/mutate_oldpy.py" --list 2>&1) || RC=$?
chk "D 버전 미달 → 종료 코드" "$RC" "2"
case "$OUT" in *"domain"*) R=수행됨;; *) R=중단됨;; esac
chk "D 버전 미달 → 작업 미수행" "$R" "중단됨"

# E. 스텁으로 .py 를 직접 실행하면 **배너가 없다**
#    이것이 .py 안의 검사로는 스텁을 잡을 수 없다는 증거이고, 배너를 두는 이유다
OUT=$(PATH="$BARE" python3 "$SELF_DIR/mutate.py" --list 2>&1)
case "$OUT" in *"[mutate]"*) R=있음;; *) R=없음;; esac
chk "E 스텁 직접 실행 → 배너" "$R" "없음"

# F. 정상 환경에서는 그대로 동작한다 (역검증이 항상 실패하면 그것도 고장이다)
RC=0; OUT=$(bash "$SELF_DIR/pyguard.sh" "$SELF_DIR/mutate.py" --list 2>/dev/null) || RC=$?
chk "F 정상 → 종료 코드" "$RC" "0"
chk "F 정상 → 변이 세트 4종" "$(printf '%s\n' "$OUT" | grep -c '건$')" "4"

echo
if [ "$fail" -eq 0 ]; then echo "  통과 $pass 건 — 가드가 전부 발동한다"; else echo "  ❌ 실패 있음 (통과 $pass 건)"; fi
exit "$fail"
