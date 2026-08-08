#!/usr/bin/env python3
"""
k6 시계열(--out json)에서 **SIGTERM 순간의 인플라이트 요청**을 세고,
그 이후 요청들이 어떻게 끝났는지 분류한다.

    python3 shutdown-analyze.py <k6.json> <sigterm_epoch>

★ 이 스크립트의 존재 이유
  "실패 0" 은 두 가지를 뜻한다 — **보호됐거나, 그 순간 요청이 없었거나.**
  Phase 3의 "쿼리 0개가 개선이 아니라 401", Phase 5의 "비밀번호 0건이 빈 로그"와 같은 형태다.
  구분하려면 **종료 시점에 실제로 처리 중이던 요청이 있었는지**를 먼저 세야 한다.
  그 값이 0이면 실패율이 무엇이 나오든 **측정 자체가 무효**다.

각 http_req_duration 포인트는 종료 시각(time)과 소요(value, ms)를 갖는다.
시작 시각 = 종료 − 소요. 따라서 start < T < end 인 요청이 SIGTERM 시점의 인플라이트다.
"""
import json
import sys
from datetime import datetime

for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8")
    except Exception:
        pass

if len(sys.argv) not in (3, 4):
    sys.stderr.write("사용법: shutdown-analyze.py <k6.json> <sigterm_epoch> [추출파일]\n")
    raise SystemExit(2)

path, T = sys.argv[1], float(sys.argv[2])
# ★ k6 --out json 원본은 요청마다 지표 포인트를 남겨 **수백 MB**가 된다(실측 347MB).
#   저장소에 넣을 수 없다. 그렇다고 요약만 남기면 나중에 다른 각도로 못 본다.
#   ±5초 창의 원시 라인만 추려도 10MB였다 — 그 정도 크기의 JSON 은 아무도 열지 않는다.
#   그래서 **100ms 버킷 × 상태별 건수**로 남긴다. 수십 줄이면서
#   "언제부터 끊겼는가 / 언제까지 성공했는가"라는 모양은 그대로 보존된다.
extract_path = sys.argv[3] if len(sys.argv) == 4 else None
WINDOW = 5.0
BUCKET = 0.1
buckets = {}   # (bucket_index, status) -> count


def epoch(ts):
    # k6는 소수점 자릿수가 들쭉날쭉하다(나노초까지 주다가 뒤 0을 떼기도 한다).
    # ★ Python 3.9 의 fromisoformat 은 소수점이 **정확히 3자리 또는 6자리**여야 한다.
    #   실측으로 걸렸다: '2026-08-08T08:46:04.78443+00:00' (5자리) -> ValueError.
    #   그래서 자르기만 하지 않고 **6자리로 채운다**.
    if "." in ts:
        head, rest = ts.split(".", 1)
        frac = ""
        for ch in rest:
            if ch.isdigit():
                frac += ch
            else:
                break
        tail = rest[len(frac):]
        ts = f"{head}.{frac[:6].ljust(6, '0')}{tail}"
    return datetime.fromisoformat(ts.replace("Z", "+00:00")).timestamp()


inflight = 0
after_2xx = after_other = 0
before_total = 0
status_after = {}
seen = 0

with open(path, encoding="utf-8") as fh:
    for line in fh:
        line = line.strip()
        if not line or '"http_req_duration"' not in line:
            continue
        try:
            p = json.loads(line)
        except Exception:
            continue
        if p.get("type") != "Point":
            continue
        d = p["data"]
        end = epoch(d["time"])
        start = end - float(d["value"]) / 1000.0
        st = str(d.get("tags", {}).get("status", "?"))
        seen += 1
        if start < T < end:
            inflight += 1
        if end > T:
            status_after[st] = status_after.get(st, 0) + 1
            if st.startswith("2"):
                after_2xx += 1
            else:
                after_other += 1
        else:
            before_total += 1
        if extract_path and abs(end - T) <= WINDOW:
            b = int((end - T) // BUCKET)
            buckets[(b, st)] = buckets.get((b, st), 0) + 1

if extract_path:
    with open(extract_path, "w", encoding="utf-8") as fh:
        fh.write("# SIGTERM 기준 100ms 버킷별 응답 상태 분포 (offset 음수 = 종료 이전)\n")
        fh.write("# offset_ms\tstatus\tcount\n")
        for (b, st) in sorted(buckets):
            fh.write(f"{b * 100}\t{st}\t{buckets[(b, st)]}\n")

print()
print("=== 측정 장치 검증 (먼저 본다) ===")
print(f"  총 요청 포인트         : {seen:,}")
print(f"  SIGTERM 이전 완료      : {before_total:,}")
print(f"  ★ SIGTERM 시점 인플라이트: {inflight}")
if inflight == 0:
    print()
    print("  ❌ 종료 순간 처리 중이던 요청이 0건이다.")
    print("     이 조건에서는 실패율이 무엇이 나오든 graceful 여부를 변별할 수 없다.")
    print("     **측정 무효** — 부하나 타이밍을 바꿔 다시 잡아야 한다.")
    raise SystemExit(1)

print()
print("=== SIGTERM 이후 종료된 요청의 결말 ===")
for k in sorted(status_after):
    label = "성공" if k.startswith("2") else ("연결 실패/중단" if k == "0" else "기타")
    print(f"  status {k:>3} : {status_after[k]:>6,}  ({label})")
print(f"  합계 2xx {after_2xx:,} / 비2xx {after_other:,}")
print()
print("  해석 — 인플라이트가 있었으므로 이 수치는 의미를 갖는다.")
print("  graceful 이면 인플라이트가 2xx 로 완료되고, immediate 면 끊긴다.")
