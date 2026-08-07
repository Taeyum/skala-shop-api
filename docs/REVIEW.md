# 자체 점검 기록

Phase 완료 시점마다 아래 체크리스트로 점검하고 결과를 **누적**한다. 지난 기록은 수정하지 않는다.
체크 실패 항목은 지우지 말고 남긴 뒤, 조치 결과를 다음 Phase 점검에 다시 적는다.
"발견한 문제"에 적은 내용 중 과정에서 배운 것은 `JOURNAL.md`에도 남긴다.

## 점검 항목 (템플릿)

```markdown
## Phase N — YYYY-MM-DD

### 계약 준수
- [ ] SPEC.md의 URI·JSON 필드가 변경되지 않았는가
- [ ] E2E 시나리오(SPEC.md 5절)가 여전히 통과하는가

### 절대 규칙
- [ ] 엔티티에 public setter가 없는가
- [ ] Controller에 엔티티가 노출되지 않았는가
- [ ] 도메인 간 Repository 직접 참조가 없는가
- [ ] Service가 웹 타입(HttpServletRequest, Cookie)에 의존하지 않는가
- [ ] 금액이 전부 BigDecimal인가 (compareTo 사용, equals 금지)

### 품질
- [ ] ./gradlew build 통과
- [ ] 스펙 이탈 항목이 DECISIONS.md에 기록됐는가
- [ ] 근거 없는 설정값(풀 크기, TTL 등)이 남아 있지 않은가

### 발견한 문제
(있으면 기록, 없으면 "없음")
```

---

## Phase 0 — 2026-08-07

### 계약 준수
- [x] SPEC.md의 URI·JSON 필드가 변경되지 않았는가
      8개 엔드포인트 URI·메서드 그대로. 요청/응답 필드명 그대로(`customerId`, `customerPoint`, `productId`, `productName`, `productPrice`, `quantity`, `products`)
- [x] E2E 시나리오(SPEC.md 5절)가 여전히 통과하는가
      curl로 6단계 전부 확인 — 가입 1,000,000 → 로그인 `bff-access` 쿠키 → 상품 3개 → 주문 970,000 → 수량 2 → 취소 985,000

### 절대 규칙
**5개 항목 전부 미준수. 의도된 것이다.** Phase 0은 개선 전 기준점이고, 이 항목들을 고치는 것이 Phase 1의 내용이다
(범위 합의 경위는 JOURNAL.md 2026-08-07 항목).

- [ ] 엔티티에 public setter가 없는가 → **있음**. `@Setter` 3종 (Phase 1: 정적 팩토리 + 행위 메서드)
- [ ] Controller에 엔티티가 노출되지 않았는가 → **노출됨**. `@RequestBody Product`, `@RequestBody Customer` (Phase 1: DTO 분리)
- [ ] 도메인 간 Repository 직접 참조가 없는가 → **있음**. `CustomerService`가 `ProductRepository` 주입 (Phase 1)
- [ ] Service가 웹 타입에 의존하지 않는가 → **의존함**. `CustomerService` → `SessionHandler` → `HttpServletRequest` (Phase 1: `@LoginCustomer`)
- [ ] 금액이 전부 BigDecimal인가 → **아님**. `Double` (Phase 1)

예외: `OrderListDto`·`OrderItemDto`·`OrderRequest`·`CustomerSession`은 Phase 0인데도 별도 클래스다.
SPEC이 요구하는 JSON 모양을 엔티티만으로 만들 수 없고, 강의 자료도 이 4개를 `data/dto/`에 두고 있다.

### E2E가 검출하지 못하는 것

**E2E 시나리오(SPEC.md 5절)는 상품 가격을 변경하지 않는다.** 그래서 가장 큰 결함인
`orderedPrice` 부재(현재가 기준 환불)를 **통과시켜 버린다.** 주문과 취소 사이에 가격이 그대로면
현재가 환불과 주문가 환불의 결과가 같기 때문이다.

즉 "E2E 통과"는 계약 이행의 증거일 뿐 정확성의 증거가 아니다. 가격을 올린 뒤 취소하는
회귀 시나리오는 Phase 1에서 `orderedPrice`를 넣을 때 테스트로 추가한다 (PLAN.md Phase 4).

### 품질
- [x] `./gradlew build` 통과
- [x] 스펙 이탈 항목이 DECISIONS.md에 기록됐는가 — 8절 신설 (스펙이 비워둔 부분의 선택 7건)
- [x] 근거 없는 설정값이 남아 있지 않은가
      HikariCP·Tomcat은 기본값 유지(Phase 3에서 측정 후 조정). `jwt.expiration-ms: 3600000`은 Phase 0 임시값임을 주석에 명시

### 강의 자료 명세 대조 (2026-08-07 추가 수행)

Phase 0 커밋 이후 원본 자료를 발견해 **항목 단위 대조**를 별도로 수행했다.

**대조 범위** — 인쇄 548~556. `ProductService` 5개 메서드, `CustomerService` 8개 메서드,
`ProductController` 5개, `CustomerController` 8개. 슬라이드의 산문(`- 항목`) 한 줄을 하나의 체크 항목으로 옮겨
코드에서 해당 동작의 존재를 직접 확인했다.

**결과**

| 구분 | 건수 | 조치 |
|---|---|---|
| 명세대로 구현됨 | 대부분 (비즈니스 로직 항목 전부) | — |
| 메서드명 불일치 | 9건 | 자료에 맞춰 정정 (`getProducts`→`getAllProducts` 등) |
| Service 반환 타입 불일치 | 전 메서드 | `Response` 반환으로 되돌림. Phase 1에서 다시 걷어낼 예정 |
| 검증 누락 | 2건 | `updateCustomer`의 포인트 검증, `ResponseException` 2-인자 생성자 |
| 메서드 부재 | 1건 | `SessionHandler.storeAccessToken` 추가 (`writeCookie`+`createToken` 통합) |
| 따르지 않기로 한 지침 | 1건 | Product ID `0L` 세팅 — SQL 로그 실측 후 미적용 (DECISIONS.md 10.2) |
| 자료 자체의 오류 | 5건 | DECISIONS.md 10.1에 기록 |

**대조 후 API 계약 재확인** — URI·HTTP 메서드·요청/응답 필드명·파라미터 기본값(`offset=0`, `count=10`)은
8개 엔드포인트 전부 변경 없음. 어긋났던 것은 자바 메서드명이지 계약이 아니다.

### 이식성 검증 (2026-08-07 추가 수행)

"채점자가 클론해서 실행한다"는 전제를 실제로 확인했다. 저장소 밖 임시 경로에 클론해
**원본 개발 컨테이너가 가동 중인 상태로** 격리 실행했다 (조건 상세는 JOURNAL.md 동일자 항목).

| 확인 항목 | 결과 |
|---|---|
| 커밋되지 않았는데 실행에 필요한 파일이 있는가 | 없음. 미추적 파일 0건, ignore 대상은 `.gradle/`·`build/`·IDE·AI 설정뿐 |
| `.env` 등 gitignore된 설정에 의존하는가 | 의존하지 않음. `.env` 없이 저장소 기본값만으로 기동 |
| `gradlew` 실행 권한 | git 인덱스 `100755`, 클론 후 `-rwxr-xr-x` |
| README 실행 방법만으로 기동되는가 | **통과.** 포트 오버라이드도 README에 문서화된 방법 |
| 문서에 없는 수동 조치가 필요했는가 | **없음.** DB 수동 생성·권한 부여·환경변수 파일 생성 모두 불필요 |
| 시드 데이터 삽입 | 정상 3건 |
| E2E 6단계 | 통과 (970,000 → 985,000) |

**발견한 결함 1건 — 즉시 수정** — `docker-compose.yml`의 `container_name` 하드코딩.
호스트 포트는 오버라이드되는데 컨테이너 이름이 고정이라 같은 머신에서 두 번째 인스턴스가
기동하지 못했다. 제거 후 재클론해 재검증 (`ce744c6`).

**검증 조건** — "통과"는 조건과 함께여야 의미가 있다.

| 항목 | 값 |
|---|---|
| 대상 | `ce744c6`을 로컬 경로에서 `git clone` → `/tmp/verify-phase0` |
| 호스트 | macOS Darwin 25.5.0 (Apple Silicon), Docker 29.6.2 / Compose v5.3.1 |
| 동시 상태 | **원본 개발 컨테이너가 5433에서 가동 중인 채로** 격리 실행 |
| 포트 | `POSTGRES_PORT=5434`, `SERVER_PORT=9090` (README 문서화 범위) |
| 볼륨 | 신규 생성. 실행 전 잔존 볼륨 없음을 확인 (이전 실행 데이터로 통과하는 것을 방지) |
| 정리 | `down -v` + 이미지 삭제 + 디렉터리 삭제, 잔존 0건. 원본 무영향 확인 |

**검증하지 않은 범위** — 아래는 이번 검증이 보증하지 못한다.

| 미검증 범위 | 실제 리스크 |
|---|---|
| 다른 물리 머신 | 낮음. 저장소 외부 의존이 없음을 파일 목록으로 확인 |
| 리눅스 호스트 | 중간. 이미지는 리눅스지만 호스트 포트 바인딩 동작이 macOS와 다르다 (JOURNAL 2026-08-06 참조) |
| **오프라인·폐쇄망** | **높음.** 아래 문제 표에 등록 |
| x86_64 아키텍처 | 중간. ARM에서만 빌드했다. `postgres:16-alpine`·`eclipse-temurin`은 멀티아치를 제공하나 확인하지 않음 |
| 반복 기동 | 낮음. 2회 기동 시 시드 중복 없음은 별도 확인함 |

### 발견한 문제

**대부분 Phase 0 기준점이므로 지금 고치지 않는다.** 해당 Phase에서 처리하고 그때 이 목록을 다시 확인한다.
이미 해결된 항목은 그렇게 표시한다.

| 문제 | 처리 Phase |
|---|---|
| `jwt.secret`이 평문으로 저장소에 커밋됨 | 2 (JWT 하드닝) |
| 쿠키에 HttpOnly·Secure·SameSite 없음 | 2 |
| 비밀번호 평문 저장 (BCrypt 미적용) | 2 |
| ~~목록 조회 응답에 비밀번호 노출~~ | **해소됨** — Phase 1 4단계 DTO 분리. `CustomerResponse`에 필드 자체가 없다 |
| `PUT`/`DELETE`에 본인 확인 없음 (BOLA) | 2 |
| ~~가입 시 `customerPoint`를 클라이언트가 지정 가능 (Mass Assignment)~~ | **해소됨** — Phase 1 4단계. `CustomerRequest`에 `customerPoint` 필드가 없어 받을 방법 자체가 사라졌다 |
| `quantity` 음수 검증 없음 — 주문 시 포인트가 늘어남 | 2 |
| 비즈니스 예외가 전부 HTTP 200 — 클라이언트가 HTTP 레벨에서 성공/실패를 구분할 수 없다. 바디를 파싱해야만 알 수 있어 표준 HTTP 클라이언트·게이트웨이·모니터링이 에러를 못 잡는다 | 2 (Error → 상태 매핑) |
| 음수 포인트 검증 실패가 `DATA_NOT_FOUND`로 나간다 — 검증 실패인데 "데이터를 찾을 수 없음"이라 매핑 시 400이 아닌 **404**가 된다. 자료의 에러 코드 설계가 부정확한 지점으로, 의도적으로 보존한 것 (DECISIONS.md 10·11.3절) | 2 (Error → 상태 매핑) |
| **`offset` 해석 미확정** — 페이지 번호로 구현돼 있어 `offset=1&count=10`이 11번째가 아닌 **101번째**부터 반환한다. 강의 자료에 근거가 없어 외부 확인 대기 | 외부 확인 대기 |
| 고객 삭제 시 주문이 남아 있으면 FK 제약 위반 → 500 | 2 (참조 무결성 정책) |
| 취소 환불이 주문 시점이 아닌 **현재 가격** 기준 | 1 (`orderedPrice` 스냅샷) |
| 고객 주문 조회에서 `item.getProduct()`마다 SELECT (N+1) | 3 |
| 동시 주문 시 포인트 Lost Update 가능 | 3 (`@Version`) |
| 테스트 코드 없음 | 4 |
| `docker-compose.yml`의 `container_name` 하드코딩 — 포트를 바꿔도 같은 머신에서 두 번째 인스턴스가 뜨지 못한다 | **해결됨** (`ce744c6`) |
| **오프라인 환경에서 이미지 빌드 실패** — `Dockerfile`이 빌드 단계에서 `./gradlew dependencies`·`bootJar`를 돌려 Gradle 의존성을 새로 내려받는다. 인터넷이 없으면 `docker compose up`이 빌드에서 멈춘다 | 5 (운영·배포) |
| **채점 환경이 폐쇄망이면 실행 불가** — 위와 같은 원인. 베이스 이미지(`postgres:16-alpine`, `eclipse-temurin`) 풀도 레지스트리 접근이 필요하다. 대응은 사전 빌드된 이미지 배포·오프라인 의존성 캐시 동봉·jar 직접 제출 등이 있으나 **채점 환경을 모르는 상태에서 미리 대응하는 것은 근거 없는 대비**다. Phase 5에서 배포 방식을 정할 때 함께 판단한다 | 5 (운영·배포) |

### Phase 1 진행 중 — 절대 규칙 해소 현황 (2026-08-07, 7단계에서 완료)

Phase 0 점검에서 **5개 전부 미준수**로 기록했던 항목의 현재 상태다.
(위 Phase 0 기록은 그 시점의 사실이므로 수정하지 않는다. 정식 Phase 1 자체 점검은 7단계 완료 후 수행한다.)

| 절대 규칙 | 상태 | 근거 |
|---|---|---|
| 엔티티에 public setter가 없는가 | ✅ 해소 (5단계) | `@Setter` 제거, `@NoArgsConstructor(PROTECTED)`. 엔티티 3종에 setter 0개 |
| Controller에 엔티티가 노출되지 않았는가 | ✅ 해소 (4단계) | 요청·응답 모두 DTO. `@RequestBody Product`·`Customer` 제거 |
| Service가 웹 타입에 의존하지 않는가 | ✅ 해소 (6단계) | `@LoginCustomer` ArgumentResolver로 이동. Service import에 `jakarta.servlet`·`springframework.web`·공통 `Response` 없음 |
| 금액이 전부 BigDecimal인가 | ✅ 해소 (3단계) | `@Column(precision=19, scale=2)`. 비교는 `compareTo`, 생성은 문자열 생성자 |
| 도메인 간 Repository 직접 참조가 없는가 | ✅ 해소 (7단계) | `OrderService` 분리. 각 Service가 자기 Repository만 의존한다 |

### Phase 0 기준점 커밋

Phase 3(N+1·락)·Phase 6(성능) 의 **개선 전/후 비교는 이 시점을 대상으로 한다.**
어느 커밋인지 특정 가능해야 하므로 태그로 고정한다.

```bash
git rev-parse phase0-baseline      # 해시 확인
git show phase0-baseline           # 내용 확인
git diff phase0-baseline..HEAD     # 이후 변경 전체
```

**태그: `phase0-baseline`** — Phase 0 마감 커밋(이 점검 기록을 담은 커밋)에 붙인다.

**왜 `449b74f`(`feat: 스펙 기본 구현 완료`)가 아닌가** — 그 커밋은 원본 자료를 발견하기 전
상태라 클래스명·패키지가 자료와 다르고, 명세된 검증(`isAnyEmpty`, `customerPoint` 유효성)이
누락돼 있었다. **기준점은 "처음 작성한 코드"가 아니라 "Phase 1이 개선을 시작하는 지점"** 이어야
비교가 성립한다. 449b74f를 기준으로 삼으면 이후 측정 차이에 클래스명 정정·검증 보완 같은
성능과 무관한 변경이 섞인다.

기준점이 갖춘 조건:
- SPEC.md 계약 100% 구현, E2E 6단계 + 비즈니스 규칙 8종 통과
- 강의 자료 명세와 클래스·패키지·메서드 시그니처 일치
- 개선 대상 결함이 **의도적으로 보존된 상태** (Double 금액, 자연키 PK, 엔티티 노출,
  현재가 환불, N+1, 락 없음, HTTP 200 응답)
- 클론 후 `docker compose up`만으로 기동 확인

---

## Phase 1 — 2026-08-07

### 계약 준수
- [x] SPEC.md의 URI·JSON 필드가 변경되지 않았는가
      8개 엔드포인트 URI·메서드 그대로. 요청/응답 필드명 그대로.
      **값 표기만 바뀌었다** — 금액이 `BigDecimal(scale=2)`이 되어 `1000000.0` → `1000000.00`.
      필드명이 아니라 값 표기이고 숫자로 파싱하면 동일하다 (DECISIONS.md 3절)
- [x] E2E 시나리오(SPEC.md 5절)가 여전히 통과하는가
      단계마다 21개 항목(6단계 + 회귀 15종)을 돌렸고 7단계 후에도 전부 통과

### 절대 규칙 — 5개 전부 준수

Phase 0에서 **5개 전부 미준수**였던 것이 모두 해소됐다.

- [x] 엔티티에 public setter가 없는가 — `@Setter` 제거, `@NoArgsConstructor(PROTECTED)`. 엔티티 3종 setter 0개
- [x] Controller에 엔티티가 노출되지 않았는가 — 요청·응답 모두 DTO
- [x] 도메인 간 Repository 직접 참조가 없는가 — `OrderService` 분리. 각 Service가 자기 Repository만 의존
- [x] Service가 웹 타입에 의존하지 않는가 — `@LoginCustomer` ArgumentResolver.
      Service import에 `jakarta.servlet`·`springframework.web`·공통 `Response` 없음
- [x] 금액이 전부 BigDecimal인가 — `@Column(precision=19, scale=2)`, 비교는 `compareTo`,
      생성은 문자열 생성자

### 품질
- [x] `./gradlew build` 통과
- [x] 스펙 이탈 항목이 DECISIONS.md에 기록됐는가 — 1·2·3·4·5절 갱신·신설
- [x] 근거 없는 설정값이 남아 있지 않은가 — HikariCP·Tomcat 기본값 유지 (Phase 3에서 측정 후 조정)

### Phase 1로 표시했던 문제 대조

| 문제 (Phase 0 점검 시점) | 상태 |
|---|---|
| 취소 환불이 주문 시점이 아닌 현재 가격 기준 | ✅ 해소 (2단계). 실측 확인 — 개선 전 1,020,000(원금 초과) → 개선 후 985,000 |
| 가입 시 `customerPoint` 지정 가능 (Mass Assignment) | ✅ 해소 (4단계). `CustomerRequest`에 필드가 없다 |
| 목록 조회 응답에 비밀번호 노출 | ✅ 해소 (4단계). `CustomerResponse`에 필드가 없다 |

### Phase 1에서 새로 확인한 것

- **E2E가 검출하지 못하던 결함을 별도 시나리오로 잡았다** — 주문 → 가격 변경 → 취소.
  Phase 0 점검에서 지적했던 사각지대이며, 개선 전/후를 각각 실측해 기록했다 (DECISIONS.md 2절)
- **반올림 경계 검증을 추가했다** — 85,000을 7개에 결제(÷7 나눠떨어지지 않음) 후 1개씩 7회 취소 →
  원금 정확히 복귀. 3·5·7단계에서 각각 재확인했다
- **검증 스크립트 자체의 결함을 발견하고 영향 범위를 재검증했다** — 별도 커밋 `76f0fbd`.
  과거 커밋 5개를 다시 돌려 `phase0-baseline` 포함 전부 18/18 통과를 확인했다

### 발견한 문제

Phase 1에서 새로 생긴 문제는 없다. 아래는 Phase 0에서 이월된 것 중 아직 남은 항목이다.

| 문제 | 처리 Phase |
|---|---|
| `jwt.secret` 평문 커밋 · 쿠키 플래그 없음 · 비밀번호 평문 저장 | 2 |
| BOLA (`PUT`/`DELETE`에 본인 확인 없음) | 2 |
| `quantity` 음수 검증 없음 | 2 |
| 비즈니스 예외가 전부 HTTP 200 · 음수 포인트 검증이 `DATA_NOT_FOUND` | 2 |
| 고객 삭제 시 FK 제약 위반 | 2 |
| `loginCustomer`의 `isAnyEmpty`가 Service에 남아 있음 (요청 형태 검사) | 2 (Bean Validation) |
| N+1 · 락 없음 | 3 |
| `offset` 해석 미확정 | 외부 확인 대기 |
| 테스트 코드 없음 | 4 |
| 오프라인·폐쇄망 실행 불가 | 5 |

### Phase 1 완료 커밋

**태그: `phase1-structure`**

Phase 3(N+1·락)·Phase 6(성능) 비교에서 기준선이 **둘** 필요하다.

| 기준선 | 의미 |
|---|---|
| `phase0-baseline` | 스펙 그대로. 개선 전 원점 |
| `phase1-structure` | 구조 재설계 완료. **성능 개선의 직전 상태** |

Phase 3의 N+1·락 개선은 이 구조 위에서 이뤄지므로, 성능 비교의 직접 대조군은 `phase1-structure`다.
`phase0-baseline`과의 비교는 "구조 재설계가 성능에 미친 영향"을 별도로 보여준다 —
두 기준선을 나눠야 **구조 변경 효과와 성능 최적화 효과가 섞이지 않는다.**

> **[Phase 2에서 갱신]** 위 판단은 Phase 2 시점에 **틀린 것이 됐다.** BCrypt 도입으로 로그인 경로가
> 7.6ms → 69.9ms로 늘어, `phase1-structure`를 대조군으로 쓰면 그 비용이 "성능 저하"로 읽힌다.
> Phase 3의 직접 대조군은 **`phase2-security`** 다 — 아래 "Phase 2 완료 커밋" 참조.
> (기존 기록은 수정하지 않는다는 규칙에 따라 원문을 그대로 두고 덧붙인다. 여기서 배운 것:
> **기준선은 "지금이 마지막 단계"라는 가정 위에서 정하면 다음 Phase가 그 가정을 깬다.**)

---

## Phase 2 진행 중 — 자산 기준 노출 경로 점검 (2026-08-07)

B-3에서 얻은 교훈 — **경로가 아니라 자산에서 출발한다** — 을 적용해 전체를 훑었다.
B-1에서는 "로그인 응답"이라는 **경로 하나**만 보고 사용자 열거를 막았다고 적었는데,
자산(유효한 ID 목록)에서 출발했다면 목록 API가 바로 보였을 것이다.

아래는 **자산마다 그것에 이르는 모든 경로**를 센 결과다. 위협 모델링의 기본 형태이며,
"막았다"는 주장의 범위를 정확히 하기 위한 것이다.

### 자산 1 — 비밀번호

| 경로 | 상태 |
|---|---|
| DB 저장 | ✅ BCrypt 해시 (A-1, DB 직접 조회로 확인) |
| 로그인 응답 | ✅ `CustomerResponse`에 필드 없음 |
| 목록 조회 응답 | ✅ 같은 DTO |
| 엔티티 실수 직렬화 | ✅ `@JsonIgnore` (B-4). 단 도달 경로가 없어 E2E 미확인 — Phase 4 단위 테스트 |
| 로그 | ⚠️ **미점검.** AOP 로깅이 Phase 5에 있고, 요청 바디를 찍으면 평문이 남는다. Phase 5에서 마스킹 |

### 자산 2 — 유효한 customerId 목록

| 경로 | 상태 |
|---|---|
| 로그인 응답 차이 | ✅ 401로 통일 (B-1) |
| BOLA 응답 차이 (403 vs 404) | ✅ 소유권 검사를 존재 확인보다 먼저 (B-3) |
| `GET /api/customers/list` | ❌ **열려 있다.** 익명으로 전체 ID 획득 가능 |
| 로그인 응답 시간 차이 | ⚠️ 없는 ID는 BCrypt를 건너뛰어 더 빠르다 (9-3절) |

> **이 자산은 막히지 않았다.** `/list` 하나로 전체가 나간다. 다른 세 경로를 막은 것은
> "그 경로로는 안 된다"는 의미일 뿐, **자산이 보호된다는 뜻이 아니다.**
> 완전한 차단은 역할 기반 접근 제어가 전제다 (미적용, 아래 문제 표).

### 자산 3 — 고객의 포인트 잔액

| 경로 | 상태 |
|---|---|
| 본인 조회 | ✅ 정상 기능 |
| 타인 조회 `GET /{customerId}` | ✅ 403 (B-3) |
| `GET /api/customers/list` | ❌ **열려 있다.** 전체 고객의 잔액이 익명 노출 |

### 자산 4 — 고객의 주문 이력

| 경로 | 상태 |
|---|---|
| 타인 조회 `GET /{customerId}` | ✅ 403 (B-3) |
| `GET /api/customers/list` | ✅ 목록에는 주문이 포함되지 않는다 (`customerId` + `customerPoint`만) |

### 자산 5 — 포인트 잔액의 무결성 (증감 경로)

"포인트는 음수가 될 수 없다"·"공짜로 늘지 않는다"에 이르는 경로를 셌다.

| 경로 | 상태 |
|---|---|
| `usePoint` (주문) | ✅ 잔액 검사 + 양수 검사 |
| `refundPoint` (취소) | ✅ 양수 검사 (B-2에서 발견 — **잔액 검사가 없어 음수 환불로 우회 가능했다**) |
| `changePoint` (정보 수정) | ✅ 음수 거부 + BOLA 방어 (B-2·B-3) |
| 음수·0 수량 주문·취소 | ✅ `@Positive` + 엔티티 불변식 이중 방어 (B-2) |
| 동시 요청 | ❌ **열려 있다.** 낙관적 락 미적용 — Phase 3 |

> 자산 5는 **B-2에서 자산 기준으로 훑은 덕에** `refundPoint` 구멍을 찾았다.
> `@Positive`만 붙이고 끝냈으면 주문 경로만 막고 취소 경로는 남았을 것이다.

### 정리

**"막았다"고 말할 수 있는 자산**: 비밀번호(로그 제외), 주문 이력, 포인트 무결성(동시성 제외)
**막히지 않은 자산**: 유효한 ID 목록, 포인트 잔액 — 둘 다 `GET /api/customers/list` 하나 때문이다

### Phase 2로 표시했던 문제 대조

| 문제 (Phase 0·1 점검 시점) | 상태 |
|---|---|
| 비밀번호 평문 저장 | ✅ 해소 (A-1) BCrypt. DB 직접 조회로 확인 |
| `jwt.secret` 평문 커밋 | ✅ 해소 (A-2) 환경변수. 히스토리 잔존은 DECISIONS 12절에 구분 기록 |
| 쿠키에 HttpOnly·Secure·SameSite 없음 | ✅ 해소 (A-3) `Secure`는 `COOKIE_SECURE`로 제어 |
| 비즈니스 예외가 전부 HTTP 200 | ✅ 해소 (B-1) SPEC 4절 매핑 |
| 음수 포인트 검증이 `DATA_NOT_FOUND` | ✅ 해소 (B-1) `ParameterException` 400 |
| `quantity` 음수 검증 없음 | ✅ 해소 (B-2) `@Positive` + 엔티티 불변식 |
| `loginCustomer`의 `isAnyEmpty`가 Service에 | ✅ 해소 (B-2) `@Valid` + `@NotBlank`로 Controller 이동 |
| BOLA (`PUT`/`DELETE` 본인 확인 없음) | ✅ 해소 (B-3) `NOT_OWNER` 403 |
| 고객 삭제 시 FK 제약 위반 → 500 | ✅ 해소 (B-5) `DATA_IN_USE` 409 |

### Phase 2에서 새로 발견해 처리한 것

| 발견 | 처리 |
|---|---|
| **사용자 열거** — 로그인 실패가 404/401로 갈려 계정 존재 노출 | 401로 통일 (B-1). 단 `/list`로 여전히 열려 있음 |
| **음수 환불로 잔액 검사 우회** — `refundPoint`에 검사가 없어 잔액이 음수까지 갈 수 있었다 | 양수 검사 추가 (B-2) |
| **fail-late** — 짧은 JWT 시크릿으로도 앱이 뜨고 첫 로그인에서 500 | `@PostConstruct`로 기동 시 검증 (A-2) |
| **깨진 JSON이 500** | `HttpMessageNotReadableException` 핸들러 → 400 (B-1) |
| **BOLA 응답이 계정 존재를 노출할 뻔** | 소유권 검사를 존재 확인보다 먼저 (B-3) |

### 남은 문제

| 문제 | 처리 |
|---|---|
| **`GET /api/customers/list`가 익명 공개** — 전체 `customerId` + 잔액 노출. 역할 기반 접근 제어가 전제라 이번 Phase 범위 밖. 계약 유지 결정 (DECISIONS 9-5절) | 미해결 (역할 도입 시) |
| 로그인 응답 시간으로 계정 존재 추정 (타이밍) | 미해결 (rate limiting 선행) |
| 요청 속도 제한 없음 | 미적용 |
| 요청 바디 로깅 시 비밀번호 평문 노출 가능 | 5 (AOP 로깅 + 마스킹) |
| `@JsonIgnore` 방어를 E2E로 확인 불가 | 4 (엔티티 직렬화 단위 테스트) |
| N+1 · 락 없음 | 3 |
| `offset` 해석 미확정 | 외부 확인 대기 |
| 테스트 코드 없음 | 4 |
| 오프라인·폐쇄망 실행 불가 | 5 |


---

## Phase 2 — 2026-08-07

### 계약 준수
- [x] SPEC.md의 URI·JSON 필드가 변경되지 않았는가
      URI·메서드·필드명 그대로. **HTTP 상태 코드가 200에서 SPEC 4절 매핑으로 바뀌었고**(B-1),
      `GET /api/customers/{customerId}`에 인증 요구가 추가됐다(B-3). 둘 다 SPEC.md에 반영했다 —
      문서가 코드와 다르면 그 자체가 결함이다
- [x] E2E 시나리오(SPEC.md 5절)가 여전히 통과하는가 — 48개 항목 전부 통과

### 절대 규칙 — 5개 유지
Phase 1에서 충족한 상태가 유지되는지 확인했다. 새 코드(`LoginCustomerArgumentResolver`,
`PasswordConfig`)는 `global/`에 두었고 도메인 경계를 넘지 않는다.
- [x] 엔티티에 public setter 없음
- [x] Controller에 엔티티 미노출
- [x] 도메인 간 Repository 직접 참조 없음 — 참조 무결성 확인도 DB에 위임해 경계를 지켰다(B-5)
- [x] Service가 웹 타입에 의존하지 않음
- [x] 금액 BigDecimal

### 품질
- [x] `./gradlew build` 통과
- [x] 스펙 이탈 항목이 DECISIONS.md에 기록됐는가 — 9-1 ~ 9-6절 신설
- [x] 근거 없는 설정값이 남아 있지 않은가
      BCrypt work factor는 기본값 10을 쓰고 "근거 없이 바꾸지 않는다"를 명시.
      토큰 만료 1시간은 임시값에서 근거 있는 선택으로 바꿨다(9-2절)

### Phase 2 완료 커밋 — 기준선이 셋이 된 이유

**태그: `phase2-security`**

| 기준선 | 의미 |
|---|---|
| `phase0-baseline` | 스펙 그대로. 개선 전 원점 |
| `phase1-structure` | 구조 재설계 완료 |
| `phase2-security` | 보안 강화 완료. **Phase 3 개선의 직접 대조군** |

**Phase 2에서 BCrypt가 도입되어 로그인 경로의 응답 시간이 구조적으로 늘어났다.** 해싱은
의도적으로 느린 연산이므로 이것은 결함이 아니라 **보안 강화의 비용**이다. 그 비용이 Phase 3의
성능 개선 효과와 섞이면 두 수치를 모두 못 쓰게 된다.

**실측** (같은 머신·같은 DB·5회 평균, curl `time_total`):

| 경로 | `phase1-structure` | `phase2-security` | 차이 |
|---|---|---|---|
| 로그인 | 0.0076s | **0.0699s** | **약 9배 (+62ms)** |
| 회원가입 | 0.116s | 0.191s | +75ms |
| 주문 조회 (대조군) | 0.0054s | 0.0081s | 노이즈 수준 |

로그인·가입만 늘고 **BCrypt와 무관한 주문 조회는 사실상 동일**하다. 즉 증가분의 출처가 해싱임이
대조군으로 확인된다.

Phase 6에서 로그인이 포함된 시나리오를 측정할 때 `phase1-structure`를 대조군으로 쓰면
**BCrypt 비용이 "성능 저하"로 잘못 읽힌다.** `phase0-baseline`과의 비교는 "전체 개선 여정",
`phase1-structure`와의 비교는 "구조 재설계 효과", `phase2-security`와의 비교는 "성능 최적화 효과"를
각각 보여준다 — **섞이지 않게 나누는 것이 기준선을 셋으로 두는 이유다.**

> 측정 조건은 개발 노트북의 Docker 컨테이너이며 절대 수치는 환경에 좌우된다(DECISIONS.md 13절).
> 여기서 의미 있는 것은 **경로 간 상대 차이**와 **대조군이 움직이지 않았다는 사실**이다.

---

## Phase 3 — 2026-08-07

### 계약 준수
- [x] SPEC.md의 URI·JSON 필드가 변경되지 않았는가 — 변경 없음
- [x] E2E 시나리오가 여전히 통과하는가 — **48/48**
- 새 응답 상태 하나 추가: 낙관적 락 충돌 → **409 `CONCURRENT_MODIFICATION`**.
  `SPEC.md` 4절이 이미 "낙관적 락 충돌 → 409"로 예고한 자리이므로 계약 이탈이 아니다

### 절대 규칙
- [x] 엔티티에 public setter 없음 — `@Version` 필드는 JPA가 관리하며 setter 없음
- [x] Controller에 엔티티 미노출
- [x] 도메인 간 Repository 직접 참조 없음
- [x] Service가 웹 타입에 의존하지 않음
- [x] 금액 BigDecimal — 정합성 단언도 `compareTo` 비교자를 명시해 사용
- [x] `open-in-view: false` 유지

### 품질
- [x] `./gradlew build` 통과 — **테스트 6건, 실패 0**
- [x] 스펙 이탈·설계 판단이 DECISIONS.md에 기록됐는가 — 14절 신설
- [x] 근거 없는 설정값이 없는가 — 캐싱·풀 튜닝을 **Phase 6으로 미룬 것 자체가** 이 규칙의 적용이다.
      k6 없이 정하면 "측정 결과나 명시적 근거"를 붙일 수 없다

### 이 Phase의 성격 — 측정이 결과물이다

Phase 3은 코드 변경량이 작다(`@EntityGraph` 1줄, `@Version` 1필드, 핸들러 1개).
**결과물은 코드가 아니라 그 변경이 옳다는 근거**이며, 그래서 측정 장치를 먼저 검증하는 데
시간을 더 썼다.

| 측정 | 개선 전 | 개선 후 |
|---|---|---|
| `GET /api/customers/{id}` 쿼리 수 (상품 20종) | 22 | **2** |
| 동시 주문 100건 후 포인트 오차 | **88,000** | **0** |

### 측정 장치가 먼저 틀린 사례 — 4건

이 Phase에서 **측정 대상보다 측정 장치가 더 자주 틀렸다.**

| # | 무엇이 | 어떻게 보였는가 | 무엇이 잡았는가 |
|---|---|---|---|
| 1 | grep 패턴이 로거명과 불일치 | SQL 0개 | 0이 명백히 틀린 값 |
| 2 | 측정 픽스처가 DB를 오염 | 대조군이 2 → 1로 변동 | **대조군** |
| 3 | 스레드 배리어 교착 | 소요 30,213ms | 타임아웃 값과 일치 |
| 4 | TPS 비교의 분모 불일치 | 낙관적 락이 더 빠름 | 성공 14/100 |

**1·3·4는 개선 후였다면 성과처럼 보였을 것이다.** 0개 쿼리는 완벽한 N+1 해결로, 높은 TPS는
빠른 락으로 읽힌다. 2만이 "나빠 보이는" 신호였고, 그래서 유일하게 **찾지 않아도 스스로 드러났다.**

> `REVIEW.md`의 "검증하는 쪽을 검증하지 않았다"가 Phase 3에서 **네 번 재발했고 네 번 모두 잡혔다.**
> 잡힌 이유는 이번엔 **미리 방어를 설계해뒀기 때문**이다 — 대조군, 행 수 선단언, 배리어 도달 단언.
> Phase 2까지는 사후에 발견했고, Phase 3에서는 **함정이 발동할 자리에 미리 센서를 놓았다.**

### 새로 발견해 처리한 것

- **Docker Engine 29가 구버전 API를 거부해 Testcontainers가 뜨지 못했다.**
  `docker info`·`docker compose`는 정상이라 Docker 문제로 보이지 않았다.
  `api.version=1.44`로 해결. Testcontainers 버전 상향(1.20.6→1.21.3)은 **효과가 없어 되돌렸다**
- **`test` 프로파일에 `jwt.secret`이 없었다.** 첫 테스트를 쓰기 전까지 드러나지 않았다 —
  `local`·`docker`만 값을 갖고 있었다
- **Lost Update의 손실량이 커넥션 풀 크기에 종속된다.** 스레드 100개라도 DB 트랜잭션은
  풀 크기(기본 10)만큼만 동시 진행되므로 경합 폭이 고정된다. 절대값을 결론으로 쓰지 않는다

### 남은 문제

| 문제 | 상태 |
|---|---|
| `GET /api/customers/list` 익명 노출 | ❌ 계약 유지. 역할 기반 접근 제어 필요 (Phase 2에서 기록) |
| 로그인 타이밍 부채널 | ❌ 미해결 |
| 요청 속도 제한 없음 | ❌ 미해결 |
| 캐싱·HTTP 캐시 헤더·풀 튜닝 | ⏸ **Phase 6으로 보류** (측정 근거 필요) |
| 자동 재시도(`@Retryable`) | ⏸ 보류. 재시도 정책은 충돌 분포 측정 위에서 정한다 (`DECISIONS.md` 14절) |
| 재고 도입 시 락 전략 재검토 | ⏸ 상품 행 경합이 생기면 결론이 바뀐다 |
| `Product`·`OrderItem`에 `@Version` 없음 | ✅ 의도적. 동시에 갱신되는 필드가 없다 |

### Phase 3 완료 커밋

**태그: `phase3-performance`**

Phase 6 부하 측정의 대조군은 이 태그다. `phase2-security`와의 비교가 "성능 최적화 효과"를 보여준다.
