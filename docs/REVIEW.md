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

### 발견한 문제

**대부분 Phase 0 기준점이므로 지금 고치지 않는다.** 해당 Phase에서 처리하고 그때 이 목록을 다시 확인한다.
이미 해결된 항목은 그렇게 표시한다.

| 문제 | 처리 Phase |
|---|---|
| `jwt.secret`이 평문으로 저장소에 커밋됨 | 2 (JWT 하드닝) |
| 쿠키에 HttpOnly·Secure·SameSite 없음 | 2 |
| 비밀번호 평문 저장 + 목록 조회 응답에 노출 | 2 |
| `PUT`/`DELETE`에 본인 확인 없음 (BOLA) | 2 |
| 가입 시 `customerPoint`를 클라이언트가 지정 가능 (Mass Assignment) | 2 |
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
