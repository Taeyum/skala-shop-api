#!/usr/bin/env python3
"""
예외·실패 분기 전수 조사.

JaCoCo 리포트를 **클래스 백분율이 아니라 라인·분기 단위**로 열어
"밟히지 않은 곳"을 목록으로 뽑는다.

왜 이 도구가 필요한가 — Phase 4에서 `SessionHandler` 커버리지 93%를 보고
"커버리지의 한계"라고 결론지었는데, 전수 조사해보니 **문제는 도구가 아니라 집계 단위**였다.
같은 리포트를 라인 단위로 열면 `catch (JwtException)` 블록은 이미 미실행으로 찍혀 있었다.
`LoginCustomerArgumentResolver`는 **라인 100%인데 분기 1/2** 였다.

    커버리지는 "이 줄이 실행됐다"만 말한다. "이 줄이 옳은지 확인됐다"고는 말하지 않는다.
    다만 "밟히지 않은 곳"에 대해서는 도구가 이미 정확하다. 우리가 안 봤을 뿐이다.

선행 조건: ./gradlew test jacocoTestReport (XML 리포트가 있어야 한다)
사용법:    python3 docs/verify/branch-audit.py
"""
import os
import subprocess
import sys
import xml.etree.ElementTree as ET

# ── 인터프리터 선점 검사 (근거와 한계는 mutate.py 의 같은 블록 주석 참조) ──
# 요약: 낡은 인터프리터는 여기서 잡고, MS Store 스텁은 여기서 잡을 수 없다
# (스텁은 이 파일을 실행하지 않는다). 스텁 방어는 docs/verify/pyguard.sh 에 있다.
# 배너가 없으면 이 파일은 실행되지 않은 것이다 — 이 도구는 "미실행 목록이 비었다"가
# 정상 결과일 수 있어서, 침묵과 성공이 특히 헷갈린다.
# Windows 기본 stdout 인코딩(cp949)에서 한글이 깨진다 — 진단 메시지보다 먼저 고정한다
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8")
    except Exception:
        pass

_MIN = (3, 8)
if sys.version_info[:2] < _MIN:
    sys.stderr.write(
        "\n  ❌ python %d.%d 이상이 필요하다 (현재 %s)\n     실행 파일: %s\n\n"
        % (_MIN[0], _MIN[1], ".".join(map(str, sys.version_info[:3])), sys.executable))
    raise SystemExit(2)
sys.stderr.write("  · [branch-audit] python %s @ %s\n"
                 % (".".join(map(str, sys.version_info[:3])), sys.executable))

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
REPORT = os.path.join(ROOT, 'build/reports/jacoco/test/jacocoTestReport.xml')


def load_coverage():
    if not os.path.exists(REPORT):
        print(f"리포트가 없다: {REPORT}\n먼저 ./gradlew test jacocoTestReport 를 실행할 것")
        sys.exit(1)
    coverage = {}
    for package in ET.parse(REPORT).getroot().iter('package'):
        for source in package.findall('sourcefile'):
            for line in source.findall('line'):
                coverage[(package.get('name') + '/' + source.get('name'), int(line.get('nr')))] = (
                    int(line.get('ci')), int(line.get('mi')),
                    int(line.get('cb')), int(line.get('mb')))
    return coverage


def throw_sites():
    out = subprocess.run(['grep', '-rn', r'throw new\|} catch (', 'src/main/java'],
                         cwd=ROOT, capture_output=True, text=True).stdout.strip()
    return [line.split(':', 2) for line in out.split('\n') if line]


def main():
    coverage = load_coverage()

    print('═══ throw · catch 지점이 테스트에서 실행되는가 ═══')
    print(f"  {'위치':<44}{'행':>5}  {'밟힘':>8}  코드")
    unreached = 0
    for path, number, code in throw_sites():
        rel = path.replace('src/main/java/', '')
        key = (os.path.dirname(rel) + '/' + os.path.basename(rel), int(number))
        entry = coverage.get(key)
        if entry is None:
            mark = '?'
        elif entry[0] > 0:
            mark = '✅'
        else:
            mark = '❌ 안밟힘'
            unreached += 1
        short = rel.replace('com/sk/skala/shopapi/', '')
        print(f"  {short:<44}{number:>5}  {mark:>8}  {code.strip()[:60]}")

    print('\n═══ 미실행 라인 · 부분 커버 분기 전수 ═══')
    print('  (이 목록은 "덮어야 할 빚"이 아니라 "다음에 어디를 볼지의 지도"다)')
    for package in ET.parse(REPORT).getroot().iter('package'):
        for source in package.findall('sourcefile'):
            gaps = []
            for line in source.findall('line'):
                nr, ci, mi = int(line.get('nr')), int(line.get('ci')), int(line.get('mi'))
                cb, mb = int(line.get('cb')), int(line.get('mb'))
                if mi > 0:
                    gaps.append((nr, '라인 미실행'))
                elif mb > 0:
                    gaps.append((nr, f'분기 {cb}/{cb + mb}만 실행'))
            if not gaps:
                continue
            java = os.path.join(ROOT, 'src/main/java', package.get('name'), source.get('name'))
            lines = open(java).read().split('\n') if os.path.exists(java) else []
            print(f"\n  {package.get('name').replace('com/sk/skala/shopapi/', '')}/{source.get('name')}")
            for nr, why in gaps:
                text = lines[nr - 1].strip()[:66] if nr - 1 < len(lines) else ''
                print(f"    {nr:>4}  {why:<16} {text}")

    print(f"\n밟히지 않은 throw/catch: {unreached}건")
    print('※ "밟혔다"는 "검증됐다"가 아니다. 확신이 필요한 곳은 mutate.py 로 확인할 것')


if __name__ == '__main__':
    main()
