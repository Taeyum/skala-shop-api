# 검증 스크립트

Phase 0~6에서 실제로 쓴 도구들이다. `docs/perf/`(k6 부하 측정)와 짝을 이룬다.
**저장소 밖 임시 디렉터리에 두었다가 다른 머신에서 이어받을 수 없다는 것을 깨닫고 옮겼다.**

모든 스크립트는 절대 경로를 쓰지 않는다 — 저장소 루트를 스크립트 위치에서 구하고,
대상 주소는 `B` 환경변수로 바꿀 수 있다(`B=http://localhost:18080 bash ...`).

## 회귀 검증

| 스크립트 | 내용 |
|---|---|
| `e2e.sh` | **48건.** 매 커밋 전에 돌린 회귀 방어선 |
| `core-phase0-1.sh` | 18건. ⚠️ **옛 태그 전용** — 현재 코드에서는 계약이 바뀌어 9/18 |

```bash
docker compose up -d          # 또는 ./gradlew bootRun
bash docs/verify/e2e.sh       # 통과 48 건 이어야 한다
```

`e2e.sh`가 검증하는 것: SPEC 5절 E2E 6단계 · 비즈니스 규칙 회귀(재주문 누적, 전량취소 행삭제,
주문 당시 가격 환불) · 수량 검증 · 참조 무결성(`DATA_IN_USE`) · BOLA 방어(403) ·
HTTP 상태 매핑 · 사용자 열거 방어.

> **`chk`가 인자를 3개 받았는지 검사하는 이유** — Phase 1에서 JSON 페이로드를 중첩 따옴표로
> 넘겼다가 bash 중괄호 확장이 걸려 `{"a":1,"b":2}`가 두 단어로 쪼개졌다. `chk`가 인자를 4개 받아
> `$2`와 `$3`이 **둘 다 `PARSE_ERR`가 되어 "통과"로 판정**됐다. 5개 커밋이 거짓 통과로 기록됐고
> 전부 되돌아가 재검증해야 했다. 그때 실제로 막아준 것은 문서가 아니라 이 인자 수 검사다.

## 변이 테스트 — "실패할 수 없는 테스트는 없는 테스트보다 나쁘다"

```bash
python3 docs/verify/mutate.py --list              # 세트 목록
python3 docs/verify/mutate.py arch                # 아키텍처 규칙 10건
python3 docs/verify/mutate.py domain layer arch exception   # 전체 46건
```

운영 코드를 일부러 깨뜨려 테스트가 **실제로 실패하는지** 확인한다. 통과만 보고 넘어가면
그 테스트가 무엇을 지키는지 알 수 없다.

| 세트 | 건수 | 대상 |
|---|---|---|
| `domain` | 9 | 경계값, 검증 제거, 반올림 방식, 총액 누적 누락 |
| `layer` | 12 | 사용자 열거 부활, 소유권 확인 순서, `@Valid` 제거, 핸들러 제거 |
| `arch` | 10 | ArchUnit 규칙을 하나씩 위반 |
| `exception` | 15 | 에러 코드 교체, 상태 매핑, 500 유출, 리졸버 |

**★ 안전망이 도구의 일부다.** Phase 4에서 이 스크립트의 이전 판이 10분 타임아웃 SIGKILL로
종료되며 `SessionHandler`·`CustomerService`에 **보안을 약화시키는 변이를 남겼다.**
`finally`는 SIGKILL을 잡지 못한다. 그래서 ① `finally` ② SIGTERM 핸들러
③ `git checkout -- src/main/java` (프로세스 밖) ④ 실행 전 작업 트리 clean 검사 를 모두 둔다.

## 커버리지 분석

```bash
./gradlew test jacocoTestReport
python3 docs/verify/branch-audit.py
```

JaCoCo 리포트를 **클래스 백분율이 아니라 라인·분기 단위**로 열어 "밟히지 않은 곳"을 뽑는다.

> Phase 4에서 `SessionHandler` 커버리지 93%를 보고 "커버리지의 한계"라고 결론지었는데,
> 전수 조사해보니 **문제는 도구가 아니라 집계 단위**였다. 같은 리포트를 라인 단위로 열면
> `catch (JwtException)`은 이미 미실행으로 찍혀 있었고, `LoginCustomerArgumentResolver`는
> **라인 100%인데 분기 1/2** 였다.
>
> 커버리지는 "이 줄이 실행됐다"만 말한다. 다만 **"밟히지 않은 곳"에 대해서는 도구가 이미 정확하다.**

## 성능·동작 측정

| 스크립트 | 내용 |
|---|---|
| `bench.sh` | 응답 시간. **대조군을 함께 본다** — BCrypt 도입 때 "무관한 경로가 안 움직였다"가 원인을 확정했다 |
| `n1-measure.sh` | N+1 측정. 쿼리 수보다 **반환 행 수를 먼저 단언**한다 |
| `sql-aggregate.py` | SQL 로그를 쿼리 종류별로 집계. `python3 sql-aggregate.py app.log` |
| `graceful-shutdown.sh` | ⚠️ Phase 5에서 **변별에 실패한** 측정. 헤더에 경위와 다음 시도 방향이 있다 |

부하 테스트(k6)는 `docs/perf/` 참조.

## 다른 머신에서 이어받을 때

1. `git clone` + `git fetch --tags`
2. `CLAUDE.md`와 `.claude/rules/`를 별도 보관본에서 복사 (`.gitignore` 대상이라 clone에 없다)
3. `docker compose up -d --build` → `bash docs/verify/e2e.sh` 로 48/48 확인
4. **성능 기준값은 다시 잡는다.** `docs/evidence/perf/`의 수치는 Apple M5 / 10코어 /
   Docker VM 10CPU·7GB 에서 잰 것이다. 기존 파일은 지우지 말 것 — 상단에 머신 사양이 박혀 있어
   "다른 조건의 측정"으로 구분된다
