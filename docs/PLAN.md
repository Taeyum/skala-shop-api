# 구현 계획

한 번에 한 Phase만 진행한다. Phase 완료 시 체크박스를 갱신하고 커밋한다.

---

## Phase 0 — 스펙 100% 구현 ⭐ 최우선

개선 전 기준점. **반드시 커밋으로 못 박는다** (N+1·성능·락 개선의 비교 대상이 된다).
구현 순서: Entity → Repository → Service → Controller

> **범위** — Phase 0은 스펙 그대로(개선 전 상태) 구현한다. `CLAUDE.md` 절대 규칙은 **완성 상태의 규칙**이고,
> 그 규칙을 충족시키는 것이 Phase 1의 내용이다. 경위는 `JOURNAL.md` 2026-08-07 항목.

- [x] Gradle 프로젝트 초기화, `build.gradle` 의존성
- [x] 엔티티 3종 (Product, Customer, OrderItem)
- [x] Repository 3종
- [x] Service 2종 (ProductService, CustomerService)
- [x] Controller 2종
- [x] `Response`, `PagedList`, `SessionHandler`(JWT)
- [x] `Error` enum, `ResponseException`·`ParameterException`, `GlobalExceptionHandler`
- [x] `application.yml`, `data.sql` 시드
- [x] **E2E 시나리오 전체 통과** (`SPEC.md` 5절) — curl로 6단계 + 비즈니스 규칙 8종 확인
- [x] 커밋: `feat: 스펙 기본 구현 완료`

착수 후 추가로 수행한 것 (원본 자료를 뒤늦게 발견해 생긴 작업)

- [x] 강의 자료 원본 대조 — 클래스명·패키지 구조를 자료와 일치 (`data/table`·`data/dto`·`common`·`exception`)
- [x] Service·Controller 명세 항목 단위 대조 — 메서드명 9건, 검증 2건, 반환 타입 정정
- [x] 자료의 오류 5건 판별 및 기록 (`DECISIONS.md` 11.1)
- [x] 자료 지침 미적용 근거를 SQL 로그로 실측 (`docs/evidence/`)
- [x] Phase 0에서 자료를 따를지 판단하는 기준 명문화 (`DECISIONS.md` 10절)
- [x] **이식성 검증** — 저장소 밖 클론에서 `docker compose up`만으로 기동·E2E 통과 확인
- [x] 자체 점검 및 기준점 태그 고정 (`REVIEW.md`, 태그 `phase0-baseline`)

> **기준점** — Phase 3·6의 개선 전/후 비교는 태그 **`phase0-baseline`** 을 대상으로 한다.
> 상세는 `REVIEW.md` "Phase 0 기준점 커밋".

## Phase 1 — 구조 재설계

> **순서** — 항목 간 의존이 있어 아래 순서로 진행했다. 각 단계마다 E2E 21건을 돌리고 커밋했다.

- [x] `Customer` 대리키 전환 — PK `Long id`, `customerId`는 UNIQUE NOT NULL *(1단계)*
- [x] `OrderItem`에 결제 총액 스냅샷(`orderedAmount`) 추가 + `(customer_id, product_id)` 복합 UNIQUE *(2단계)*
      단가가 아니라 총액인 이유는 `DECISIONS.md` 2절
- [x] `BigDecimal` 전환 (`@Column(precision=19, scale=2)`) *(3단계)*
- [x] Request/Response DTO 분리 — 엔티티 노출 제거 *(4단계)*
- [x] 풍부한 도메인 모델 — setter 제거, 정적 팩토리, 행위 메서드 *(5단계)*
- [x] `@LoginCustomer` ArgumentResolver — Service에서 SessionHandler 제거 *(6단계)*
      Service의 `Response` 반환도 제거했다
- [x] 도메인 기준 패키지로 재배치 (product / customer / order / global) *(7단계)*
- [x] 도메인 간 Repository 직접 참조 제거 — `OrderService` 분리 *(7단계)*

> **기준선** — Phase 3·6의 성능 비교는 태그 **`phase1-structure`** 를 직접 대조군으로 쓴다.
> `phase0-baseline`과의 비교는 구조 재설계가 성능에 미친 영향을 별도로 보여준다.
> 상세는 `REVIEW.md` "Phase 1 완료 커밋".

## Phase 2 — 보안 · 검증

> **순서** — 자격증명(A)을 먼저, 설계 결함(B)을 나중에. BCrypt는 저장 형식이 바뀌어 다른 작업과 섞으면
> 디버깅이 어렵다. 각 단계마다 E2E를 돌리고 커밋했다.

- [x] BCrypt 비밀번호 해싱 (`spring-security-crypto`) *(A-1)*
- [x] ~~Mass Assignment 차단 — 가입 시 포인트 서버 고정~~ **Phase 1 4단계에서 해소** (`CustomerRequest`에 필드 없음)
- [x] 비밀번호 전 구간 차단 — 응답 DTO(Phase 1 4단계) + `@JsonIgnore` 이중 방어 *(B-4)*
- [x] BOLA 방어 — 본인 리소스만 수정/삭제/조회 (403 `NOT_OWNER`) *(B-3)*
- [x] Bean Validation (`@NotBlank`, `@Positive`, `@Valid`) *(B-2)*
- [x] Error → HTTP 상태 매핑 *(B-1)*
- [x] JWT 하드닝 — 환경변수 시크릿, 만료시간 근거, HttpOnly/Secure/SameSite *(A-2·A-3)*
- [x] 500 유출 차단 — 스택트레이스는 로그만, 응답엔 traceId *(B-1)*
- [x] 삭제 시 참조 무결성 정책 결정 및 적용 — 거부(`DATA_IN_USE` 409) *(B-5)*

## Phase 3 — 동시성 · 성능

- [x] `open-in-view: false` + SQL 로그 설정
- [x] N+1 해결 — `@EntityGraph` (22 → 2 쿼리)
- [x] **개선 전/후 SQL 로그 캡처** → `docs/evidence/n-plus-1.md`
- [x] 쿼리 카운트 테스트 (Hibernate Statistics) — `OrderQueryCountTest`, 역검증 완료
- [x] `@Version` 낙관적 락 — Lost Update 88건 증발을 먼저 실측 (`docs/evidence/lost-update.md`)
- [x] 락 충돌 후처리 — 409 `CONCURRENT_MODIFICATION`. `@Retryable`은 넣지 않았다 (`DECISIONS.md` 14절)
- [x] ~~`@Cacheable` + `@CacheEvict` 상품 조회 캐싱~~ **Phase 6으로 보류** →
      구현 완료 → **측정 결과 이득이 없어 제거했다** (2026-08-08).
      상한 조건(트래픽 100%가 캐시 대상·포화)에서도 p95 효과 0, req/s는 편차 안.
      반면 다중 인스턴스에서 **낡은 값을 무한히** 준다. 근거는 `DECISIONS.md` 25절 ⑤
- [x] ~~HTTP 캐시 헤더 (`Cache-Control`, `ETag`)~~ **Phase 6으로 보류** →
      **Phase 6에서 구현 완료.** ⚠️ 단 **전송량 절감은 미측정** (`DECISIONS.md` 25절)
- [x] ~~HikariCP · Tomcat 스레드풀 튜닝~~ **Phase 6으로 보류** — k6 없이 정하면 근거 없는 숫자가 된다
      → **Phase 6에서 측정 완료, 그리고 값을 확정하지 않기로 결론냈다.**
      두 머신에서 두 축 모두 결론이 뒤집혀 **튜닝값이 환경 종속임이 실증**됐다.
      보류할 때의 우려("근거 없는 숫자")가 옳았고, **측정 후에도 근거가 서지 않았다** (26절)

> **보류 항목** — 캐싱·HTTP 캐시 헤더·풀 튜닝은 **측정 근거가 필요한 항목**이라 Phase 6과 함께 판단한다.
> k6가 없는 상태에서 숫자를 정하면 "설정값에는 측정 결과나 명시적 근거가 붙어야 한다"를 스스로 어긴다.
>
> **N+1 측정 시 주의 ①** — 1차 캐시 때문에 동일 상품 반복 주문으로는 재현되지 않는다.
> 서로 다른 상품 20개 이상으로 시드를 구성하고, 측정 직전 `em.clear()`로 캐시를 비운다.
>
> **N+1 측정 시 주의 ② — 측정 대상이 실제로 일했는지 먼저 확인한다.**
> 조회 결과가 **비어 있으면 쿼리가 1개만 나가** "개선됐다"로 보인다. 쿼리 수를 세기 전에
> **반환된 행 수를 먼저 단언**한다(예: 주문 20건이 실제로 조회됐는가). 인증이 걸린 뒤로는
> 쿼리 자체가 401로 튕겨도 "쿼리 1개"가 되므로 더 그렇다.
> `REVIEW.md`의 "검증하는 쪽을 검증하지 않았다"에서 **Phase 3·6의 형태로 예측해둔 함정**이다 —
> 측정값이 나온다고 측정이 된 것은 아니다.
>
> **기준선** — Phase 3의 개선 전/후 대조군은 **`phase2-security`** 다.
> `phase1-structure`를 쓰면 BCrypt 도입 비용이 개선 효과에 섞인다 (`REVIEW.md` "Phase 2 완료 커밋").

## Phase 4 — 테스트

- [x] 도메인 단위 테스트 (순수 JUnit, Spring 없이) — 32건, 0.27초. 변이 9건 전부 잡힘
- [x] Service 유닛 테스트 (Mockito) — 23건
- [x] `@DataJpaTest` Repository 쿼리 검증 — **Testcontainers PostgreSQL 기반** — 10건
      (`replace = NONE`이 필요하다. `.withReuse(true)`는 **실측 결과 효과가 없었다** — `DECISIONS.md` 17절)
- [x] `@WebMvcTest` Controller 검증 — 20건.
      슬라이스가 `HandlerMethodArgumentResolver`·`@ControllerAdvice`를 자동 포함한다
- [x] `@SpringBootTest` + MockMvc E2E — `ShopScenarioTest` 7건 (SPEC 5절 6단계 + 미인증 401)
- [x] **동시성 테스트** — `ExecutorService` + `CountDownLatch`, 100건 동시 주문 포인트 정합성
      *(Phase 3에서 선수행 — `ConcurrentOrderTest`)*
- [x] 낙관적 락 vs 비관적 락 비교 실험 → 처리량·실패율 표 *(Phase 3에서 선수행 — `docs/evidence/lock-comparison.md`)*
- [x] 트랜잭션 롤백 시나리오 — 잔액 부족 시 주문 미저장, 취소 실패 시 수량 불변
- [x] ArchUnit — 규칙 12개. **순환 참조 실제 위반을 찾아 해소했다.** 변이 10건 전부 잡힘
- [x] JaCoCo + 커버리지 게이트 (라인 85% / 분기 80%, 실측 89.4% / 87.2%)
      제외 사유는 `build.gradle` 주석과 `DECISIONS.md` 16절. Lombok 생성분은 `lombok.config`로 제외
- [x] 인증 테스트 — `SessionHandlerTest` 8건 *(계획에 없던 항목. 아래 참조)*

> **Phase 4에서 나온 것** — 커버리지 **93%** 인 `SessionHandler`에 **위조 토큰을 받아들이는 회귀가
> 테스트 104건을 모두 통과했다.** 커버리지는 "실행됐다"만 말하고 "옳은지 확인됐다"고는 말하지 않는다.
> 예고해둔 함정(`JOURNAL` Phase 4)이 실제로 발동한 것이며, 이 항목은 계획에 없다가 그래서 추가됐다.
>
> ArchUnit은 첫 실행에서 `customer ↔ order` **패키지 순환**을 찾았다 — `DECISIONS.md` 4절은
> 단방향이라고 적고 있었고 사람은 세 Phase 동안 못 봤다.
>
> 모든 테스트를 역검증했다 (도메인 9 · 계층 12 · 아키텍처 10 · 게이트 1 = 32건).
> 상세는 `DECISIONS.md` 15~17절.

## Phase 5 — 운영 · 배포

- [x] Swagger / OpenAPI (`springdoc-openapi-starter-webmvc-ui`)
      쿠키 인증이 브라우저에서 그대로 동작한다. `SWAGGER_ENABLED` 스위치 (`DECISIONS.md` 19절)
- [x] AOP API 로깅 — 실행시간 + 비밀번호 마스킹
      **첫 검증이 거짓 음성이었다** (로그가 아무것도 안 남기고 있었다). 20절
- [x] MDC traceId — 모든 로그 + 응답 헤더 `X-Trace-Id`. 500 응답의 traceId와 일치
- [x] Actuator health / readiness / liveness — **화이트리스트**로 health만. 21절
- [x] Graceful shutdown — 기능 활성 확인. **효과는 측정하지 못했다** (REVIEW 참조)
- [x] 프로파일 분리 (local / test / docker) + `docker-compose.yml`
- [x] Dockerfile 하드닝 — non-root, layered jar, JRE, 컨테이너 인식 JVM
      **크기는 줄지 않았다** — 160MB → 168MB(이전에도 멀티스테이지+JRE). 이득은 재배포 전송량 56.9MB → 393kB
- [x] ~~**HA 다중 인스턴스** — app 2개 + 로드밸런서~~
      **Phase 6으로 미룬다.** 전제(무상태 JWT, traceId 이어받기)는 갖췄으나
      다중 인스턴스의 값은 처리량으로 보여야 하고 그 측정 도구가 Phase 6에서 준비된다
      → **Phase 6에서 완료.** 다만 미룰 때의 전제였던 *"값은 처리량으로 보여야 한다"* 는
      **단일 머신에서 성립하지 않았다** — 자원을 나눠 쓰므로 인스턴스를 늘리면 처리량이 줄었다.
      값은 처리량이 아니라 **무상태·장애 내성**으로 드러났다
- [x] 배포 롤백 절차 문서화 (이미지 태그 전략) — **절차를 실제로 실행해 확인**했다. 23절
- [x] JPA Auditing (`createdAt`, `updatedAt`)
      `data.sql`과 `@DataJpaTest` 슬라이스가 깨졌고 NOT NULL 제약이 둘 다 잡았다
- [x] 오프라인 빌드 대응 판단 — **미리 대비하지 않고 절차만 문서화**한다. 22절

> **Phase 5에서 나온 것** — Actuator의 닫힌 엔드포인트를 두드려보다
> **404·405·415·타입불일치가 전부 500 + ERROR 로그(스택트레이스 48줄)** 인 것을 발견했다.
> Phase 2 이후 줄곧 그랬고, 테스트가 전부 **실재하는 경로만** 호출해서 드러나지 않았다.
> 별도 커밋으로 교정하고 테스트 4건으로 고정했다.
>
> **Phase 6 대조군은 `phase5-operations`다.** AOP 로깅·MDC·Auditing이 요청당 상시 비용을
> 더한다 — 주문 조회 4.1ms → 5.3ms (+29%). `phase3-performance`를 쓰면 운영 계측 비용이
> 튜닝 효과에 섞인다 (`REVIEW.md` "Phase 6 대조군 판단").

## Phase 6 — 성능 측정 (k6)

- [x] 측정 하네스 (`docs/perf/`) + 측정 장치 자체 검증 *(계획에 없던 항목)*
- [x] 부하 테스트 — p95, TPS — 성공 100% · p95 7.16ms · 740 req/s
- [x] 스트레스 테스트 — 한계점 탐색 — 800 VU · 4,150 req/s. ⚠️ **한계점 미도달**
- [x] 스파이크 테스트 — 10 → 150 VU, 폭증 후 회복
- [x] 동시 주문 정합성 시나리오 — VU 50 / 2만 건 **불일치 0**
- [x] 설정 스윕 (HikariCP 풀 · Tomcat 스레드 · 반복 편차) *(계획에 없던 항목)*
- [x] **결함 교정** — 동시 첫 주문의 제약 위반이 500으로 나가던 것을 409로 *(계획에 없던 항목)*
- [x] ~~**튜닝값 확정 및 재측정**~~ → **확정하지 않기로 결론냈다.**
      두 머신에서 풀·스레드 **두 축 모두 결론이 뒤집혔다**(풀 2: macOS 최선 → Windows 최악).
      **튜닝값이 환경에 종속된다는 것이 측정의 산출물**이고, 기본값 유지가 그 결론의 귀결이다.
      VU 축 스윕도 하지 않는다 — 같은 결론에 도달하는 비용만 더한다 (`DECISIONS.md` 26절)
- [x] **캐시 · ETag의 성능 효과 측정** — **ETag 전송량 −51% (유지) / `@Cacheable` 이득 없음 (제거)**
      같은 "캐싱"이지만 아끼는 대상이 달라 판단이 갈렸다 (`DECISIONS.md` 25절)
- [x] `graceful shutdown` k6 재검증 (Phase 5에서 이월) — **동작 확인, 효과는 관측 조건 부재**
      graceful 창의 실제 길이 **66ms**. 보호 대상(≤20건)이 실행 간 편차(약 300건)보다
      한 자릿수 작아 원리적으로 분해되지 않는다 (`DECISIONS.md` 28절)
- [x] **HA 다중 인스턴스** (Phase 5에서 이월) — app 2개 + 로드밸런서, 무상태 검증
      `docker-compose.ha.yml`(nginx + app 2 + 공유 PostgreSQL). **응용 코드 무변경.**
      무상태·분산·장애 내성 ✅ / **처리량은 늘지 않고 줄었다**(단일 머신 자원 공유).
      "HA를 구현했다"가 아니라 **"앱 계층의 수평 확장 가능성을 실증했다"** 가 정확하다 —
      DB·LB는 여전히 단일 지점이다 (`DECISIONS.md` 27절)
- [x] **개선 전(Phase 0 커밋) vs 개선 후 비교표** — 재측정 없이 **이미 측정된 값의 종합**
      개선(N+1 22→2, Lost Update 88,000→0, 테스트 0→160, ETag −51%)과
      **비용(BCrypt +62ms, AOP·MDC +29%)을 함께** 적었다. `phase0-baseline` 재빌드는
      하지 않았다 — 그 시점에 `.gitattributes`가 없어 현재 머신에서 빌드되지 않는다
      (`DECISIONS.md` 29절)

      > 당초 계획은 *"운영과 같은 DB 엔진이므로 병목의 성격은 논할 수 있다. 같은 머신·같은
      > compose 설정에서 측정한 상대 비교로 서술한다"* 였다. **"같은 머신"이라는 전제가 깨졌다** —
      > 작업 도중 macOS에서 Windows로 옮겼고, `phase0-baseline`은 현재 머신에서 빌드조차 되지 않는다.
      > 그래서 처리량 재측정 대신 **각 Phase가 남긴 측정 기록의 종합**으로 대체했다.

> **★ 기준값을 다시 잡아야 한다 — 머신이 바뀌었다.**
> `docs/evidence/perf/`의 수치는 **macOS · Apple M5 10코어 · Docker VM 10CPU/7GB** 에서 잰 것이다.
> 이후 작업은 **Windows + Docker Desktop(WSL2)** 에서 이어진다. 절대 수치는 비교 대상이 아니며,
> **같은 머신에서 대조군부터 다시 측정**해야 개선 전/후 비교가 성립한다.
> 기존 파일은 지우지 않는다 — 상단에 머신 사양이 박혀 있어 "다른 조건의 측정"으로 구분된다
> (`docs/verify/README.md` "다른 머신에서 이어받을 때" 4번).
>
> **Phase 6에서 나온 것** — 부하 측정의 산출물이 성능 수치가 아니라 **정확성 결함**이었다.
> 성공률 99.99%에 묻혀 있던 500을, 풀 스윕에서 `resp_5xx`를 따로 세면서 찾았다.
> Phase 3 동시성 테스트가 이걸 놓친 이유는 **"경합 지점을 하나만 남긴다"는 좋은 격리 설계가
> 정확히 이 경우를 배제했기 때문**이다 — `JOURNAL.md` Phase 6 "검증의 네 번째 요소는 조건이다".
>
> **기록이 커밋 메시지에만 있었다.** Phase 6의 발견 경위와 설계 근거가 세 문서 어디에도 없었고,
> 2026-08-08에 소급 작성했다(`DECISIONS.md` 24~26절 · `JOURNAL.md` Phase 6 · `REVIEW.md` Phase 6 진행 중).
> 소요 시간은 복원하지 못해 커밋 간격 상한으로만 남겼다. 경위는 `REVIEW.md` 해당 절.

> **Phase 6 마감** — 태그 **`phase6-benchmark`**.
> 산출물은 성능 수치가 아니라 **판단**이었다 — 튜닝값을 확정하지 않았고, 캐시를 지웠고,
> 409 해석을 철회했다. 미해결 6건은 각각 "왜 지금 하지 않는가"와 함께 `REVIEW.md`에 있다.

## Phase 7 — 보고서

개발 완료 후 별도 작성. `DECISIONS.md`를 원본 자료로 사용.

- [ ] 설계 의사결정 (대리키, 스냅샷, 패키지 구조, 동시성 방식)
- [ ] 발견한 스펙 결함과 대응 (Mass Assignment, BOLA, 환불 가격, 수량 검증)
- [ ] N+1 개선 전/후 근거
- [ ] 성능 측정 결과
- [ ] **미적용 패턴과 그 이유** — 서킷브레이커·벌크헤드(격리할 외부 의존성이 DB 하나뿐), CDN(정적 자원 없음)
- [ ] 기술 선택의 근거 — H2 대신 PostgreSQL을 쓴 이유와 그 덕에 측정 가능해진 것들
- [ ] MSA 전환 로드맵 — ID 참조 전환, Saga 패턴, Resilience4j, DB 스키마 분리
- [ ] 한계 — 단일 DB 인스턴스(복제·페일오버 없음), offset 페이징의 한계
