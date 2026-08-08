#!/bin/bash
# 파이썬 인터프리터 선점 검사. 두 가지 방식으로 쓴다.
#
#   . "$(dirname "$0")/pyguard.sh"          → $PY 에 동작하는 인터프리터가 담긴다
#   bash pyguard.sh <스크립트.py> [인자...]  → 검사한 뒤 그 인터프리터로 실행한다
#
# ★ 왜 파일 안이 아니라 여기에 방어를 두는가
#
#   Windows에서 `python3` 이 Microsoft Store 의 **앱 실행 별칭(스텁)** 으로 잡히는 일이 있다.
#   스텁은 인자를 무시하고 `Python ` 한 줄만 찍은 뒤 exit 49 로 끝난다.
#   즉 **대상 .py 파일을 아예 실행하지 않는다.**
#
#   그래서 .py 안에 아무리 정교한 검사를 넣어도 그 검사는 돌지 않는다 — 구조적으로 불가능하다.
#   스텁을 잡으려면 방어가 **호출 경계**에 있어야 하고, 그 자리가 이 파일이다.
#   (.py 쪽에는 "낡은 인터프리터 거부 + 시작 배너"를 따로 둔다. 역할이 다르다)
#
#   실제로 이 저장소가 그 구멍을 갖고 있었다 — `e2e.sh` 의 JSON 파서가
#   `python3 -c ... 2>/dev/null || echo PARSE_ERR` 였다. 인터프리터가 죽은 것과
#   값이 틀린 것이 **같은 결과로 뭉개진다.**
#   Phase 1의 `chk` 인자 수 검사와 정확히 같은 자리다 — **문서가 못 막는 것을 코드가 막는다.**

# 출력 인코딩을 고정한다 — Windows의 파이썬은 stdout 인코딩이 cp949 로 잡혀
# 한글 출력이 통째로 깨진다(실측). 진단 메시지가 읽히지 않으면 방어의 의미가 없다.
export PYTHONIOENCODING=utf-8

# 표식을 인터프리터가 직접 출력하게 한다.
# "실행됐다"를 종료 코드가 아니라 **출력 내용**으로 확인하는 것이 핵심이다 —
# 스텁도 종료 코드는 내지만 이 표식은 낼 수 없다.
_pyguard_probe() {      # $1 = 실행 파일, $2 = 추가 인자("" 또는 "-3")
  command -v "$1" >/dev/null 2>&1 || return 1
  _pg_out=$("$1" $2 -c 'import sys; print("PYGUARD_OK %d %d" % sys.version_info[:2])' 2>/dev/null) || return 1
  case "$_pg_out" in
    "PYGUARD_OK "*) ;;
    *) return 1 ;;      # 스텁은 여기서 걸린다 — "Python " 에는 표식이 없다
  esac
  _pg_major=${_pg_out#PYGUARD_OK }; _pg_major=${_pg_major%% *}
  _pg_minor=${_pg_out##* }
  [ "$_pg_major" -ge 3 ] 2>/dev/null || return 1
  [ "$_pg_major" -gt 3 ] || [ "$_pg_minor" -ge 8 ] || return 1
  return 0
}

pyguard_resolve() {
  PY=""
  # PYTHON 으로 강제 지정할 수 있다 — 인터프리터가 여러 개 섞인 환경 대비
  for _pg_cand in "${PYTHON:-}" python3 python; do
    [ -n "$_pg_cand" ] || continue
    if _pyguard_probe "$_pg_cand" ""; then PY="$_pg_cand"; return 0; fi
  done
  if _pyguard_probe py "-3"; then PY="py -3"; return 0; fi   # Windows 런처
  return 1
}

if ! pyguard_resolve; then
  {
    printf '\n  ❌ 동작하는 python3 을 찾지 못했다.\n\n'
    printf '     확인한 후보: ${PYTHON}, python3, python, py -3\n'
    printf '     각 후보에 -c 로 표식 출력을 시켰고 아무것도 표식을 내지 못했다.\n\n'
    printf '     Windows 라면 `python3` 이 Microsoft Store 스텁일 가능성이 높다.\n'
    printf '     확인:  which -a python3   (경로에 WindowsApps 가 보이면 그것이다)\n'
    printf '     해결:  ① 실제 파이썬 설치 폴더에 python.exe 를 python3.exe 로 복사하거나\n'
    printf '            ② 설정 > 앱 > 앱 실행 별칭 에서 python/python3 을 끄거나\n'
    printf '            ③ PYTHON=/경로/python 을 지정해 다시 실행한다\n\n'
  } >&2
  exit 1
fi

# 무엇이 선택됐는지 남긴다. 침묵보다 한 줄이 낫다 —
# 나중에 로그만 보고 "어떤 인터프리터로 잰 결과인가"를 알 수 있어야 한다.
printf '  · python: %s (%s)\n' "$($PY -c 'import sys;print(sys.version.split()[0])')" "$($PY -c 'import sys;print(sys.executable)')" >&2

# 직접 실행됐다면 런처로 동작한다
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  if [ $# -lt 1 ]; then
    printf '사용법: bash %s <스크립트.py> [인자...]\n' "$0" >&2
    exit 2
  fi
  exec $PY "$@"
fi
