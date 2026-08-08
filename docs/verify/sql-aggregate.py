import sys,re
from collections import Counter

# 인터프리터 선점 검사 — 근거와 한계는 mutate.py 의 같은 블록 참조.
# 이 도구는 "총 0 개"가 정상 결과일 수 있어(로그에 SQL이 없을 때) 침묵과 구분이 특히 어렵다.
for _s in (sys.stdout, sys.stderr):   # Windows cp949 에서 한글이 깨진다
    try:
        _s.reconfigure(encoding="utf-8")
    except Exception:
        pass
if sys.version_info[:2] < (3, 8):
    sys.stderr.write("\n  ❌ python 3.8 이상이 필요하다 (현재 %s / %s)\n\n"
                     % (".".join(map(str, sys.version_info[:3])), sys.executable))
    raise SystemExit(2)
sys.stderr.write("  · [sql-aggregate] python %s @ %s\n"
                 % (".".join(map(str, sys.version_info[:3])), sys.executable))
L=open(sys.argv[1]).read().split('\n')
idx=[i for i,l in enumerate(L) if re.search(r'\bo(rg)?\.h(ibernate)?\.SQL\b',l)]
out=[]
for k,i in enumerate(idx):
    end=idx[k+1] if k+1<len(idx) else len(L)
    stmt=' '.join(x.strip() for x in L[i:end] if x.strip())
    stmt=re.sub(r'^.*?\.SQL\s*:\s*','',stmt)
    out.append(re.sub(r'\s+',' ',stmt).strip())
print(f"총 {len(out)} 개")
for s,n in Counter(out).most_common():
    print(f"  {n:3d}회  {s[:100]}")
