# 보고서용 캡처 색인

Phase 7 보고서에 넣을 자료. **각 파일이 어느 부에 쓰이는지** 표시한다.
전부 `git rev-parse HEAD` 시점을 파일 머리에 박아뒀다.

| 파일 | 내용 | 쓰이는 곳 |
|---|---|---|
| `c1-e2e-48.txt` | E2E 48건 전체 통과 | **1부** 실행 결과 · **2부** 결함 회귀 방어 |
| `c2-coverage.txt` | JaCoCo 라인 91.4% / 분기 86.3% (게이트 85/80) | **4부** 품질 확보 |
| `c3-tests.txt` | 테스트 160건 계층별 분포 | **0부** 범위표 · **4부** |
| `c4-defects-after.txt` | 스펙 결함 6건의 조치 후 동작 | **2부** (핵심) |
| `c5-swagger.txt` | OpenAPI 엔드포인트 13개 목록 | **1부** API 목록 |
| `c6-offset.txt` | `offset` = 페이지 번호임을 실측 | **3부** 설계 판단 · **7부** 미해결 |
| `c7-ha.txt` | 분산 / 무상태 / 장애 내성 / 헤더 비활성 | **6부** 운영 · **5부** 측정 |

## 이미 저장소에 있어 새로 찍지 않은 것

캡처를 새로 만들지 않고 **기존 증거 파일을 그대로 인용**한다.

| 자료 | 위치 | 쓰이는 곳 |
|---|---|---|
| N+1 개선 전/후 SQL 로그 (22 → 2) | `docs/evidence/n-plus-1.md` | **5부** |
| Lost Update 실측 (오차 88,000 → 0) | `docs/evidence/lost-update.md` | **5부** |
| 낙관적 vs 비관적 락 비교표 | `docs/evidence/lock-comparison.md` | **5부** |
| 부하·스트레스·스파이크·정합성 (macOS) | `docs/evidence/perf/before-*.txt` | **5부** |
| 설정 스윕 (풀·스레드, 두 머신) | `docs/evidence/perf/sweep-*.txt` | **5부** |
| 캐시 낡은 값 6/6 재현 | `docs/evidence/perf/ha-cache-risk.txt` | **5부** |
| 캐시 on/off · ETag −51% | `docs/evidence/perf/win-detail-*.txt` | **5부** |
| HA 처리량 (단일 vs LB+1 vs LB+2) | `docs/evidence/perf/win-ha-load*.txt` | **5부** |
| graceful shutdown (창 66ms) | `docs/evidence/perf/win-shutdown-*.txt` | **5부** |
| 409 사전 예측과 판정 | `docs/evidence/perf/ha-prediction.md` | **5부** 반증 |

> **캐시 낡은 값 재현은 이제 다시 찍을 수 없다.** `@Cacheable`을 제거했기 때문이다.
> `ha-cache-risk.txt`가 그 현상이 존재했다는 유일한 기록이다 —
> **결함을 고치면 그 증거는 기록에만 남는다.**

## 형식 방침 — 텍스트는 인용용, 이미지는 지면용

**터미널 출력·API 응답·측정 결과는 `.txt`로 유지한다.** 복사·검색이 되고 보고서에
코드블록으로 그대로 들어간다. **둘을 함께 둔다 — 텍스트를 이미지로 대체하지 않는다.**

**다만 아래 둘은 이미지가 필요하다.** 공지가 화면 캡처를 포함한 PDF를 요구하고,
색상·레이아웃이 정보를 전달하는 자료라 텍스트로는 전달되지 않는다.

## 스크린샷 — 전 API 촬영 완료 (17장)

**13개 엔드포인트를 Swagger에서 실제로 실행해 찍었다.** 브라우저 확장으로 촬영했다.

| 파일 | 무엇 | 화면에 담긴 것 | 쓰이는 곳 |
|---|---|---|---|
| `s1`·`s1b-swagger-full` | Swagger 전체 | 엔드포인트 13개, 태그 3그룹 | **1부** |
| `s2-swagger-login` | 로그인 | 200 · `skala01`/1,000,000 · `x-trace-id` | **1부** |
| **`s2b-swagger-order`** | **주문** | **curl에 인증 헤더가 없는데 200** — 쿠키 자동 | **1부·6부** |
| `s3-jacoco` | 커버리지 리포트 | 패키지별 색상 막대, Total 92%/86% | **4부** |
| `s4-products-list` | 상품 목록 | 시드 3건 · `etag` 헤더 | **1부** |
| **`s5-customer-orders`** | **주문 조회** | **970,000 · 무선마우스 수량 2** | **1부** |
| **`s6-cancel`** | **취소** | 200 → 잔액 **985,000** | **1부** |
| **`s7-product-detail-etag`** | 상품 상세 | **`cache-control: max-age=60,public` + `etag`** | **5부** |
| **`s8-product-create`** | 상품 등록 | **요청 `id:999` → 응답 `id:4`** | **2부·3부** |
| `s9-product-update` | 상품 수정 | 200 · id를 바디로 받는 SPEC 계약 | **1부** |
| **`s10-product-delete-409`** | 상품 삭제 | **409 `DATA_IN_USE`** — 주문된 상품 | **2부·3부** |
| **`s11-customer-signup-massassign`** | 회원가입 | **요청 `customerPoint:999999999` → 응답 `1000000`** | **2부** |
| **`s12-bola-403`** | 남의 계정 조회 | **403 `NOT_OWNER`** | **2부** |
| `s13-customer-list` | 고객 목록 | 2명 · **비밀번호 필드 없음** | **1부·2부** |
| `s14-customer-update` | 정보 수정 | 200 · 본인만 | **1부** |
| **`s15-customer-delete-409`** | 탈퇴 | **409 `DATA_IN_USE`** — 보유 상품 있음 | **2부·3부** |

### ★ SPEC 5절 E2E가 브라우저만으로 완주됐다

`s2` → `s2b` → `s5` → `s6` 네 장이 이어진다.

```
가입/로그인 1,000,000  →  주문(무선마우스×2)  970,000  →  조회 확인  →  취소(×1)  985,000
```

**Swagger에서 토큰을 따로 넣는 절차가 없다** — `s2b`의 curl 명령에 인증 헤더가 없는데 200이다.
19절이 "쿠키 인증이 브라우저에서 그대로 동작한다"고 적은 것의 지면 증거다.

### ★ 요청과 응답이 한 화면에 있는 것이 값이 크다

`s8`·`s11`은 **보낸 것과 받은 것이 같은 화면에** 있다.
"서버가 클라이언트 지정 값을 무시한다"를 말이 아니라 대조로 보인다.

## 터미널 캡처 t1~t7 (완료)

**Git Bash 에서 직접 실행해 촬영했다.** 터미널 창은 브라우저 확장이 제어하지 못한다.

| 파일 | 명령 | 화면의 요점 | 쓰이는 곳 |
|---|---|---|---|
| `t1-compose-ps.png` | `docker compose ps` | app·postgres **healthy** · 5433 포트 정책 | **1부** |
| `t2-e2e-48.png` | `bash docs/verify/e2e.sh` | **48/48** · 첫 줄에 pyguard 인터프리터 확인 | **1·2부** |
| `t3-build.png` | `./gradlew clean build` | **BUILD SUCCESSFUL** · `executed` · 커버리지 게이트 포함 | **4부** |
| `t4-archunit.png` | `--tests '*ArchitectureTest*' --rerun-tasks` | ArchUnit 실행 (건수는 `s16` 리포트) | **4부** |
| `t5-concurrent.png` | `--tests '*ConcurrentOrderTest*'` | 동시성 테스트 통과 (수치는 아래 코드블록) | **5부** |
| `t6-k6.png` | `bash docs/perf/run.sh load.js ...` | k6 요약 · **하네스의 DB 초기화 전후** | **5부** |
| `t7-ha.png` | HA `ps` + 분산 카운트 | **앱에 호스트 포트 없음** · 분산 18/22 | **6부** |

### t5 에 붙일 수치 (터미널에 한글이 cp949 로 깨져 텍스트로 보관)

```
[동시성] 스레드 100 · 성공 14 · 실패 {ObjectOptimisticLockingFailureException=86} · 774ms
[동시성] 최종 포인트 986000.00 · 기대 986000.00 · 차이 0.00
[첫 주문 경합] {성공=5, DataIntegrityViolationException=9, ObjectOptimisticLockingFailureException=16}
```

**`차이 0.00` 이 5부의 핵심이다.** 락이 없던 시절엔 100건이 전부 "성공"이면서 오차가 88,000이었다 —
**실패 86건이 늘어난 것이 곧 개선이다.**

### 캡처를 만들며 나온 결함 3건

터미널 캡처 작업 자체가 결함을 찾았다. 전부 고치고 커밋했다.

| 무엇 | 어디서 | 조치 |
|---|---|---|
| **`bash` 가 WSL 로 잡힌다** | `cmd.exe` 에서 t2 실행 | README Windows 절에 실행 방법 명시 (`685af6e`) |
| **`gradlew build` 가 캐시로 2초 만에 성공** | t3 | `clean build` 로 강제 실행. **성공 화면이 일을 안 했다는 증거 없이 나온 사례** |
| **`run.sh` 가 compose 경로를 변환 없이 넘김** | t6 | `hostpath()` 적용 + 네트워크 없으면 중단 (`7c4ede8`) |

> **t6 의 실패는 가드가 잡았다** — `❌ k6 요약에 http_reqs 가 없다 — 측정이 성립하지 않았다`.
> Phase 6 에서 넣은 장치가 제 역할을 했고, 빈 결과 파일이 증거 폴더에 남는 것을 막았다.
> **4부 "실패는 시끄러워야 한다"의 실제 작동 기록이다.**

## (이전 계획) 스크린샷 계획 — s1 ~ s9

**텍스트(.txt)와 이미지(.png)를 함께 둔다.** 보고서에는 **스크린샷은 증거로, 코드블록은 가독성으로**
같이 넣는다 — 터미널 출력을 이미지로만 실으면 글씨가 작아 읽히지 않는다.

| 파일 | 무엇 | 스택 | 쓰이는 곳 |
|---|---|---|---|
| `s1-swagger-full.png` | Swagger UI 전체 (엔드포인트 13개) | 단일 | **1부** |
| `s2-swagger-order.png` | Swagger 에서 로그인 → 주문 → 조회 | 단일 | **1부** |
| `s3-jacoco.png` | JaCoCo 리포트 (패키지별 색상 막대) | — | **4부** |
| `s4-e2e.png` | E2E 48/48 | 단일 | **1·2부** |
| `s5-build.png` | `./gradlew build` 160건 통과 | Docker | **4부** |
| `s6-archunit.png` | ArchUnit 12건 | Docker | **4부** |
| `s7-concurrent.png` | 동시성 테스트 — 포인트 오차 0 | Docker | **5부** |
| `s8-k6.png` | k6 실행 화면 | 단일 | **5부** |
| `s9-ha.png` | HA 인스턴스 분산 | **HA** | **6부** |
| `s10-compose-ps.png` | `docker compose ps` 상태 | 단일/HA | **1·6부** |

> **순서 주의** — `s9`(HA)는 스택을 바꿔야 한다. 단일 구성으로 하는 것(`s1`,`s2`,`s4`,`s8`)을
> 먼저 끝내고 마지막에 HA로 전환한다. `s3`,`s5`,`s6`,`s7`은 스택과 무관하다.

## [완료] 촬영은 브라우저 확장으로 제가 수행했다

앞서 "확장이 연결돼 있지 않아 직접 촬영이 필요하다"고 적었으나, 확장을 연결한 뒤
**17장 전부 자동으로 촬영했다.** JaCoCo 는 `file://` 제약을 피하려 HTTP(포트 8090)로 띄웠다.

**터미널 출력은 여전히 이미지로 만들지 않았다** — 브라우저 확장은 Chrome 만 제어한다.
터미널 쪽은 `c1`~`c7` 의 `.txt` 로 확보돼 있고, 보고서에는 코드블록으로 넣는다
(글씨 크기 때문에 그편이 읽힌다).

## 캡처를 만들며 발견한 것

**`REVIEW.md`의 `offset` 서술이 틀렸다.** 미해결 항목 표에 "현행(건너뛸 개수)"이라고 적었는데,
코드·주석·Swagger·실제 동작이 모두 **페이지 번호**였다(`offset=1` → id 3,4).
`c6-offset.txt`로 실측해 확인하고 `REVIEW.md`를 정정했다.

> 마감 문서를 쓸 때 원본(`DECISIONS.md` 8절)을 다시 읽지 않고 기억으로 적은 결과다.
> **캡처를 만드는 작업이 문서 검증을 겸했다.**
