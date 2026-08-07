import sys,re
from collections import Counter
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
