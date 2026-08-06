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
- [x] `Error` enum, 예외 3종, `GlobalExceptionHandler`
- [x] `application.yml`, `data.sql` 시드
- [x] **E2E 시나리오 전체 통과** (`SPEC.md` 5절) — curl로 6단계 + 비즈니스 규칙 8종 확인
- [x] 커밋: `feat: 스펙 기본 구현 완료`

## Phase 1 — 구조 재설계

- [ ] 도메인 기준 패키지로 재배치 (product / customer / order / global)
- [ ] `Customer` 대리키 전환 — PK `Long id`, `customerId`는 UNIQUE NOT NULL
- [ ] `OrderItem`에 `orderedPrice` 추가 (주문 시점 가격 스냅샷)
- [ ] `(customer_id, product_id)` 복합 UNIQUE 제약
- [ ] `BigDecimal` 전환 (`@Column(precision=19, scale=2)`)
- [ ] Request/Response DTO 분리 — 엔티티 노출 제거
- [ ] 풍부한 도메인 모델 — setter 제거, 정적 팩토리, 행위 메서드
- [ ] `@LoginCustomer` ArgumentResolver — Service에서 SessionHandler 제거

## Phase 2 — 보안 · 검증

- [ ] BCrypt 비밀번호 해싱 (`spring-security-crypto`)
- [ ] Mass Assignment 차단 — 가입 시 포인트 서버 고정
- [ ] 비밀번호 전 구간 차단 (응답 DTO + `@JsonIgnore` 이중 방어)
- [ ] BOLA 방어 — 본인 리소스만 수정/삭제 (403)
- [ ] Bean Validation (`@NotBlank`, `@Positive`, `@Valid`)
- [ ] Error → HTTP 상태 매핑
- [ ] JWT 하드닝 — 환경변수 시크릿, 만료시간, HttpOnly/Secure/SameSite
- [ ] 500 유출 차단 — 스택트레이스는 로그만, 응답엔 traceId
- [ ] 삭제 시 참조 무결성 정책 결정 및 적용

## Phase 3 — 동시성 · 성능

- [ ] `open-in-view: false` + SQL 로그 설정
- [ ] N+1 해결 — `@EntityGraph` 또는 fetch join
- [ ] **개선 전/후 SQL 로그 캡처** → `docs/evidence/`
- [ ] 쿼리 카운트 테스트 (Hibernate Statistics)
- [ ] `@Version` 낙관적 락
- [ ] 락 충돌 후처리 — 409 응답 또는 `@Retryable`
- [ ] `@Cacheable` + `@CacheEvict` 상품 조회 캐싱
- [ ] HTTP 캐시 헤더 (`Cache-Control`, `ETag`)
- [ ] HikariCP · Tomcat 스레드풀 튜닝 (**측정 후 근거와 함께**)

> **N+1 측정 시 주의** — 1차 캐시 때문에 동일 상품 반복 주문으로는 재현되지 않는다.
> 서로 다른 상품 20개 이상으로 시드를 구성하고, 측정 직전 `em.clear()`로 캐시를 비운다.

## Phase 4 — 테스트

- [ ] 도메인 단위 테스트 (순수 JUnit, Spring 없이)
- [ ] Service 유닛 테스트 (Mockito)
- [ ] `@DataJpaTest` Repository 쿼리 검증 — **Testcontainers PostgreSQL 기반**
      (`@ServiceConnection` + `.withReuse(true)`. 임베디드 DB로 대체하면 방언 차이를 못 잡는다)
- [ ] `@WebMvcTest` Controller 검증
- [ ] `@SpringBootTest` + MockMvc E2E 1개
- [ ] **동시성 테스트** — `ExecutorService` + `CountDownLatch`, 100건 동시 주문 포인트 정합성
      ⚠️ 테스트 메서드에 `@Transactional` 금지 (스레드가 트랜잭션 공유 못 함)
- [ ] 낙관적 락 vs 비관적 락 비교 실험 → 처리량·실패율 표
- [ ] 트랜잭션 롤백 시나리오 테스트
- [ ] ArchUnit — 계층·도메인 경계, setter 금지 강제
- [ ] JaCoCo + 커버리지 게이트 (DTO/엔티티 제외, 제외 사유 주석)

## Phase 5 — 운영 · 배포

- [ ] Swagger / OpenAPI (`springdoc-openapi-starter-webmvc-ui`)
- [ ] AOP API 로깅 — 실행시간 + 비밀번호 마스킹
- [ ] MDC traceId
- [ ] Actuator health / readiness / liveness
- [ ] Graceful shutdown
- [ ] 프로파일 분리 (local / test / docker) + `docker-compose.yml` (app + postgres, healthcheck 기반 기동 순서)
      (DB 전환에서 선구현 — 체크는 Phase 5 자체 점검에서 확인 후)
- [ ] Dockerfile 하드닝 — non-root 유저, layered jar, JRE 슬림화
      (compose 기동용 멀티스테이지 최소 구성은 DB 전환 시 선반영됨)
- [ ] **HA 다중 인스턴스** — app 2개 + 로드밸런서, 무상태(JWT) 검증, 세션 없이 라운드로빈 동작 확인
      (PostgreSQL 공유로 실증 가능해졌다 — DECISIONS.md 6절)
- [ ] 배포 롤백 절차 문서화 (이미지 태그 전략)
- [ ] JPA Auditing (`createdAt`, `updatedAt`)

## Phase 6 — 성능 측정 (k6)

- [ ] 부하 테스트 — p95, TPS
- [ ] 스트레스 테스트 — 한계점 탐색
- [ ] 스파이크 테스트
- [ ] 동시 주문 정합성 시나리오
- [ ] **개선 전(Phase 0 커밋) vs 개선 후 비교표**
      운영과 같은 DB 엔진(PostgreSQL)이므로 병목의 **성격**은 그대로 논할 수 있다.
      다만 절대 수치는 측정 머신·컨테이너 자원에 좌우되므로, **같은 머신·같은 compose 설정**에서
      측정한 상대 비교로 서술하고 측정 환경(CPU·메모리·컨테이너 제한)을 함께 기록한다

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
