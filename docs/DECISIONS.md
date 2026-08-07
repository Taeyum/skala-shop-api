# 설계 의사결정 기록

스펙과 다르게 구현한 모든 항목의 근거. **보고서의 원본 자료가 되므로 작업하면서 계속 채운다.**

형식: 무엇을 / 왜 / 트레이드오프

---

## 1. Customer PK를 자연키에서 대리키로 전환

**스펙**: `String customerId`를 PK로 사용
**변경**: `Long id`를 PK로, `customerId`는 UNIQUE NOT NULL

**근거**
- 자연키는 변경 가능성이 있다. 고객이 ID를 바꾸면 이를 참조하는 모든 FK를 갱신해야 한다
- 문자열 PK는 인덱스 크기가 크고, 자식 테이블 FK로 전파되면 저장 공간과 조인 비용이 증가한다
- 대리키는 비즈니스 의미가 없어 도메인 규칙 변경에 영향받지 않는다

**트레이드오프**: 조회 시 `customerId`로 한 번 더 찾아야 한다. UNIQUE 인덱스가 있어 비용은 무시할 수준.
**API 영향**: 없음. URI는 `/api/customers/{customerId}` 그대로 유지.

---

## 2. OrderItem에 orderedPrice 추가

**스펙**: `id`, `customer`, `product`, `quantity`만 존재
**변경**: `orderedPrice` (주문 시점 가격 스냅샷) 추가

**근거** — 스펙대로면 환불 금액이 틀린다:
```
1) 15,000원에 2개 주문      → 30,000 차감 (잔액 970,000)
2) 관리자가 50,000원으로 인상
3) 1개 취소                 → 50,000 환불 (잔액 1,020,000)  ← 원금 초과
```
주문·결제 도메인에서 가격은 **이력성 데이터**다. 현재 값을 참조하면 과거 거래가 왜곡된다.

**트레이드오프**: 정규화 관점에서 중복이지만, 이력 보존이 목적이므로 의도된 비정규화.
**API 영향**: 없음. 응답의 `productPrice` 필드는 그대로.

---

## 3. 금액 타입을 Double에서 BigDecimal로

**스펙**: `Double productPrice`, `Double customerPoint`
**변경**: `BigDecimal`, `@Column(precision = 19, scale = 2)`

**근거**: 부동소수점은 십진 소수를 정확히 표현하지 못한다 (`0.1 + 0.2 != 0.3`). 금액 계산에서 오차가 누적되면 잔액이 틀어진다.

**주의사항**
- `new BigDecimal(0.1)` 금지 → `BigDecimal.valueOf(0.1)` 또는 `new BigDecimal("0.1")`
- 비교는 `compareTo()`. `equals()`는 scale까지 비교해 `1.0 != 1.00`
- 나눗셈은 `RoundingMode` 필수

**API 영향**: 없음. JSON 직렬화 결과 동일.

---

## 4. 기술 계층 대신 도메인 기준 패키지 구조

**스펙**: `controller/`, `service/`, `repository/`, `data/`
**변경**: `product/`, `customer/`, `order/`, `global/`

**근거**: 모듈러 모놀리스. 도메인 폴더 하나가 미래의 마이크로서비스 하나에 대응한다. 기술 계층 구조는 서비스 분리 시 모든 폴더를 헤집어야 하지만, 도메인 구조는 폴더째 떼어낼 수 있다.
"Monolith First" — 처음부터 쪼개지 않고, 경계만 명확히 그어 필요할 때 분리한다.

**트레이드오프**: 강의 자료의 폴더 구조와 다르다. 클래스명과 API는 100% 동일하게 유지해 대조 가능성을 확보.

---

## 5. 인증을 Service에서 Controller로 이동

**스펙**: `CustomerService`가 `SessionHandler`를 주입받음
**변경**: `@LoginCustomer` ArgumentResolver로 Controller에서 해결, Service는 `customerId`만 받음

**근거**: 쿠키·JWT·세션은 웹 계층 관심사다. Service가 이를 알면 ① 순수 단위 테스트가 불가능하고 ② 배치·스케줄러가 같은 로직을 재사용할 수 없으며 ③ 인증 방식 변경이 도메인 로직에 파급된다.

**API 영향**: 없음.

---

## 6. H2 대신 PostgreSQL 채택 (단일 DB, H2 완전 제거)

**스펙**: H2 인메모리
**변경**: PostgreSQL 16 단일. H2는 의존성·설정에서 완전히 제거. 테스트도 Testcontainers PostgreSQL

**근거** — H2로는 이 프로젝트가 목표로 하는 **측정 자체가 성립하지 않는다**:

| 항목 | H2 인메모리 | PostgreSQL |
|---|---|---|
| 커넥션 풀 튜닝 | 같은 JVM 내 호출이라 커넥션 획득 비용이 사실상 0. 풀 크기를 바꿔도 유의미한 곡선이 없다 → **근거 있는 숫자를 만들 수 없다** | 실제 TCP 연결·`max_connections` 상한이 있어 풀 크기가 처리량에 반영된다 |
| 락 실험 | MVCC 구현과 경합 프로파일이 실 DB와 달라 낙관/비관 비교 수치를 일반화할 수 없다 | 실제 격리수준·락 대기에서 측정 |
| 방언 차이 | 운영 DB에서만 터지는 쿼리를 개발 중에 못 잡는다 | 개발·테스트·운영이 같은 방언 |
| HA 다중 인스턴스 | 인스턴스마다 독립된 데이터를 가져 실증 불가 | 공유 DB로 수평 확장 실증 가능 |

CLAUDE.md의 "설정값에는 측정 결과나 명시적 근거가 붙어야 한다"를 H2에서 지키려면 Phase 3의 HikariCP 튜닝은 "측정 불가로 미적용"이라고 쓰는 수밖에 없었다. DB를 바꾸면 그 항목이 실측 가능한 과제가 된다.

**트레이드오프**
- 실행에 Docker가 필요해졌다 → `docker compose up` 한 줄로 흡수 (README 최상단)
- 테스트가 컨테이너 기동 시간만큼 느려진다 → `.withReuse(true)`로 완화
- 시드가 PostgreSQL 방언에 묶인다 (`ON CONFLICT DO NOTHING`) → 단일 DB 방침이므로 의도된 결과

**API 영향**: 없음.
**전제**: PostgreSQL 사용은 담당 교수 승인을 받았다.

**타이밍** — Phase 0 기준점 커밋 **전에** 전환했다. Phase 6의 "개선 전/후 비교"는 양쪽이 같은 DB에서 돌아야 성립하므로, 기준점을 H2로 못 박은 뒤 바꾸면 DB 교체 효과와 최적화 효과가 섞여 비교표를 쓸 수 없게 된다.

---

## 7. 호스트 DB 포트를 5432로 노출하지 않는다

**일반적 관행**: `ports: "5432:5432"`
**변경**: `ports: "${POSTGRES_PORT:-5433}:5432"` — 호스트는 5433, 컨테이너 내부는 5432 유지

**근거**: 개발 머신에 PostgreSQL이 이미 설치돼 있는 것은 흔한데, 이때의 충돌이 **명시적으로 실패하지 않는다.**
로컬 PostgreSQL이 `127.0.0.1`·`[::1]`만 바인딩하면 Docker는 와일드카드 `*:5432` 바인딩에 **성공**한다. 포트 충돌 에러가 나지 않고, 대신 `localhost:5432`로 가는 연결이 컨테이너가 아니라 기존 로컬 DB로 조용히 흘러간다.

증상은 원인에서 멀다 — 이번에도 애플리케이션은 `Unable to determine Dialect without JDBC metadata`로 죽었고(Hibernate 6.5.2의 NPE가 원래 SQLException을 삼킨다) 로그 어디에도 포트 얘기가 없었다. 진단 비용이 실패의 사소함에 비해 지나치게 크다.

**설계 원칙**: 실패하려면 **시끄럽게** 실패해야 한다. 조용히 잘못된 대상에 붙는 기본값은 피한다.
5433도 절대 안전하지는 않으므로 `POSTGRES_PORT`로 덮어쓸 수 있게 하고 `.env.example`과 README에 변경 방법을 안내한다.

**트레이드오프**: 표준 포트가 아니라 `psql -p 5433`처럼 포트를 명시해야 한다. 조용한 오접속을 겪는 비용보다 싸다.
**적용 범위**: 호스트 노출 포트만. 컨테이너 네트워크 내부(`docker` 프로파일)는 표준 5432를 그대로 쓴다.

---

## 8. 스펙이 정하지 않아 직접 정한 것들

계약을 어긴 것이 아니라 **빈칸을 채운** 항목. Phase 0에서 판단이 필요했다.

| 항목 | 결정 | 근거 |
|---|---|---|
| `Response` 필드명 | `result` / `message` / `body` | SPEC 2절은 "공통 Response로 감싼다"만 규정하고 필드명을 정하지 않았다. 성공·실패 판별 / 사유 / 페이로드의 3분할 |
| `PagedList` 필드명 | `total` / `offset` / `count` / `list` | 위와 같음 |
| `offset` 해석 ⚠️ **미확정** | 페이지 번호 (`PageRequest.of(offset, count)`) | 아래 별도 설명 |
| 주문·취소 요청 클래스 | `OrderRequest` (productId, quantity) | 엔티티로는 이 바디를 받을 수 없다 — `OrderItem`은 `Product` 참조를 갖지 `productId`를 갖지 않는다 |
| 고객 주문 조회 응답 | `CustomerOrder` + `OrderedProduct` | SPEC 2절이 요구하는 평탄화된 `products` 배열은 엔티티만으로 만들 수 없다. Phase 0의 "엔티티 직접 노출"에서 벗어나지만 **계약이 강제한 예외** |
| 로그인 응답 바디 | 비움 (쿠키만 발급) | SPEC 2절 "응답 바디에 비밀번호를 포함하지 않는다". 고객 정보를 통째로 싣지 않는 쪽이 확실하다 |
| 비즈니스 예외의 HTTP 상태 | Phase 0은 200 + `fail` 바디 | SPEC 4절 매핑표에는 403 BOLA(Phase 2)·낙관적 락 409(Phase 3) 결과물이 섞여 있다. 즉 그 표는 **최종 목표**이지 Phase 0 산출물 명세가 아니다. 매핑은 Phase 2에서 붙인다 |

### `offset` 해석 — 확정하지 못한 항목

강의 자료(인쇄 548 `ProductService`, 550 `CustomerService`)는 **"Pageable 객체 생성"까지만 명시하고
`PageRequest.of()`에 무엇을 넣는지 제시하지 않는다.** 완성 코드는 "별도 Spring Boot 프로젝트 파일"로
배포된다고만 돼 있어(인쇄 556 각주) 확인할 수 없었다.

`Pageable`의 표준 용법인 **페이지 번호**로 해석했다. 다만 파라미터명 `offset`은 통상
**건너뛸 레코드 수**를 의미하므로, 명칭만 놓고 보면 `page`가 정확한 이름이다.

| 해석 | `offset=1&count=10`의 결과 |
|---|---|
| 페이지 번호 (현재 구현) | 101번째부터 10건 |
| 건수(행 오프셋) | 11번째부터 10건 |

건수 해석으로 바꿔야 한다면 `PageRequest.of(offset / count, count)`가 되며,
API 계약(URI·파라미터명)은 그대로이고 Service 한 줄만 고치면 된다.

**상태: 교수님 확인 대기.** E2E가 쓰는 `offset=0`에서는 두 해석의 결과가 같아 지금은 드러나지 않는다.

---

## 9. 발견한 스펙 결함과 대응

| 결함 | 위험 | 대응 |
|---|---|---|
| 가입 시 `customerPoint`를 클라이언트가 지정 | Mass Assignment — 무한 포인트 발급 | Request DTO에서 필드 제외, 서버 고정값 |
| 목록 조회가 엔티티 그대로 반환 | 해시 비밀번호 노출 | 응답 DTO + `@JsonIgnore` 이중 방어 |
| 수량 검증 없음 | `quantity: -5` → 포인트 증가 | `@Positive` + 도메인 메서드 내부 검증 |
| 수정·삭제에 본인 확인 없음 | **BOLA** (OWASP API Top 10 #1) — 타인 계정 조작 | JWT의 customerId와 대상 일치 검증, 불일치 시 403 |
| 재주문 시 동시 요청 방어 없음 | 같은 고객·상품 행이 2개 생성 | `(customer_id, product_id)` 복합 UNIQUE |
| 포인트 차감에 락 없음 | Lost Update — 동시 주문 시 차감 누락 | `@Version` 낙관적 락 |

---

## 10. 강의 자료의 오류와 해석

원본 자료라고 무비판 수용하지 않는다. 대조 과정에서 나온 오기·모순과 그 판단을 남긴다.

### 10.1 명백한 오기 — 따르지 않음

| 위치 | 자료 내용 | 판단 |
|---|---|---|
| 인쇄 546 | 실습 2-3 슬라이드 제목은 `[CustomerProductRepository.java]`인데 내용은 "**OrderItem** 엔터티를 관리하기 위해" | 폴더 구조(인쇄 556)와 `CustomerService` 필드가 모두 `OrderItemRepository`다. **3곳 중 2곳**을 따라 `OrderItemRepository`로 구현 |
| 인쇄 548 | `[ProductService.java]` 슬라이드인데 클래스 선언이 `public class CustomerService` | 다음 슬라이드에서 복사한 실수. `ProductService`로 구현 |
| 인쇄 549 | `deleteProduct` 설명에 "**저장** 후 Response 반환" | 삭제 메서드에 저장이 있을 수 없다. 삭제 후 반환 |
| 인쇄 551 | `deleteCustomer`도 동일하게 "저장 후 Response 반환" | 위와 같음 |
| 인쇄 551 | `createCustomer(Customer customerSession)` — 타입은 `Customer`인데 파라미터명이 `customerSession` | 로그인용 `CustomerSession`과 혼동한 흔적. 파라미터명은 `customer` |

### 10.2 따르지 않기로 한 지침 — 신규 Product의 ID를 `0L`로 세팅 (인쇄 549)

**구현하지 않는다.** 추측이 아니라 SQL 로그로 확인했다 → [`docs/evidence/product-id-0L-vs-null.md`](evidence/product-id-0L-vs-null.md)

`Product.id`는 `Long` 래퍼 + `@GeneratedValue(IDENTITY)`다. Spring Data의 `isNew()`는 래퍼 타입에서 `id == null`로 판정하므로, `0L`을 넣으면 non-null이라 **"이미 있는 엔티티"로 분류되어 `persist` 대신 `merge` 경로를 탄다.** merge는 id=0인 행을 찾으러 SELECT를 날리고, 없으면 결국 INSERT한다.

측정 결과 **상품 등록 1건당 쿼리 2회 → 3회**. 결과는 같고 SELECT만 늘어난다.
이 지침은 id가 원시형 `long`이던 시절(기본값 0이라 null 판정이 불가능했던) 관용구로 보인다.

### 10.3 해석으로 메운 부분

- **`updateCustomer`의 포인트 검증 오류 코드** — 자료는 "customerId와 customerPoint 유효성 체크 : 오류시 `ResponseException(Error.DATA_NOT_FOUND)`"라고 두 검사를 한 줄에 묶었다. 음수 포인트에 `DATA_NOT_FOUND`("데이터를 찾을 수 없음")를 쓰는 것은 의미가 맞지 않는다. **존재 확인은 `DATA_NOT_FOUND`, 포인트 유효성은 `ParameterException`**으로 나눴다 (SPEC.md 4절: 형식 오류 → `ParameterException` → 400)
- **`ResponseException`의 맥락 메시지** — 자료의 `("Customer not found")`를 받도록 2-인자 생성자를 뒀다. 다만 **응답 바디에는 `Error` 코드만 내보내고 메시지는 로그에만** 남긴다. 내부 사정을 클라이언트에 흘리지 않기 위해서다
- **Service가 `Response`를 반환하는 것** — 자료를 따랐다. 계층 분리 관점에서는 Service가 웹 응답 포맷을 아는 것이 바람직하지 않지만, Phase 0은 개선 전 기준점이므로 자료를 따른다. **Phase 1에서 인증 분리(`@LoginCustomer`)와 함께 걷어낸다**

---

## 11. 적용하지 않은 패턴과 이유

*(보고서 핵심 섹션 — 맥락 없는 기술 적용을 피했다는 근거)*

| 패턴 | 미적용 사유 |
|---|---|
| 서킷브레이커 · 벌크헤드 | 존재 목적은 **여러 외부 의존성 사이의 장애 격리**인데, 이 서비스의 외부 호출은 DB 하나뿐이다. 격리할 다른 대상이 없으므로 벌크헤드가 나눌 경계가 없고, DB가 죽으면 어차피 모든 요청이 실패하므로 서킷을 열어 얻을 것도 없다. DB 장애는 서킷브레이커가 아니라 커넥션 풀 타임아웃·획득 대기 상한으로 다루는 것이 정석이며, HikariCP 풀 크기·타임아웃 튜닝으로 자원 격리 개념을 실증한다 (PostgreSQL 전환으로 측정 가능해졌다 — 6절) |
| CDN | 순수 REST API로 정적 자원이 없다. 캐싱이라는 동일 목적은 `@Cacheable`과 HTTP 캐시 헤더로 달성 |
| MyBatis 혼용 | 실무에서 JPA와 MyBatis를 함께 쓰는 것은 흔하며, 그 근거는 명확하다 — 단순 CRUD는 영속성 컨텍스트·더티체킹으로 생산성이 높은 JPA가, 복잡한 집계·다중 조인·동적 조건·튜닝된 SQL은 MyBatis가 유리하다. 다만 **본 프로젝트의 API는 전부 단순 CRUD로, MyBatis가 우위를 갖는 쿼리가 존재하지 않는다.** 기존 CRUD를 XML로 옮기는 것은 기술을 목적 없이 추가하는 것이므로 도입하지 않았다. 도입 조건이 충족되는 시점: GROUP BY·HAVING을 동반한 통계성 조회, 조건 조합이 많은 동적 검색, 대량 배치 |
| `@ManyToMany` | 연결 테이블에 `quantity`, `orderedPrice` 같은 속성이 있어 표현 불가. `OrderItem`을 독립 엔티티로 승격 |
| Service 인터페이스 분리 | 구현체가 하나뿐인 인터페이스는 과설계. 단, 도메인 간 호출부는 MSA 분리 시 Client 교체를 위해 예외 |

---

## 12. 알려진 한계

- **주문 이력 부재**: 현재 `OrderItem`은 "주문 이력"이 아니라 "현재 보유 수량"에 가깝다. 취소하면 기록이 사라져 감사 추적이 불가능하다. 실무라면 `Order`(헤더) + `OrderLine`(상세) 구조로 분리해야 한다
- **offset 페이징**: 뒤 페이지일수록 느려지고, 조회 중 데이터 변경 시 항목이 중복·누락된다. 대용량에서는 커서 기반 페이징 필요
- **성능 수치의 해석**: 개발 노트북의 Docker 컨테이너 기준이다. 운영과 같은 DB 엔진을 쓰므로 병목의 **성격**(락 대기, 커넥션 획득 대기, 인덱스 미스)은 유효하지만, 절대 수치는 하드웨어·컨테이너 자원 할당에 좌우된다. 개선 전후를 **같은 머신·같은 컨테이너 설정**에서 측정한 상대 비교로 서술한다
- **단일 DB 인스턴스**: 복제·페일오버를 구성하지 않았다. DB가 죽으면 서비스 전체가 멈춘다. 실무라면 read replica와 자동 페일오버가 필요하다
- **`jwt.secret`이 커밋 히스토리에 남아 있다**: Phase 0 커밋(`449b74f`)의 `application.yml`에 시크릿이 평문으로 들어갔다. Phase 2에서 환경변수로 옮겨도 **히스토리에는 그대로 남는다** — 파일에서 지우는 것은 유출을 되돌리지 못한다.
  학습용 더미 값이고 이 저장소가 Phase 0 기준점을 보존해야 하므로 **히스토리 재작성(`filter-repo`) 대신 문서화로 갈음한다.**
  실무라면 순서가 반대다: ① 즉시 시크릿 로테이션(유출된 값 무효화) → ② 히스토리 정리 → ③ 재발 방지(`git-secrets`·pre-commit 훅). 지금 하지 않는 이유는 "괜찮아서"가 아니라 "값이 가짜라서"다
