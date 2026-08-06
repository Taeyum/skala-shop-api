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

예외: `CustomerOrder`·`OrderedProduct`·`OrderRequest`는 Phase 0인데도 별도 클래스로 뒀다.
SPEC이 요구하는 JSON 모양을 엔티티만으로 만들 수 없어서다 (DECISIONS.md 8절).

### 품질
- [x] `./gradlew build` 통과
- [x] 스펙 이탈 항목이 DECISIONS.md에 기록됐는가 — 8절 신설 (스펙이 비워둔 부분의 선택 7건)
- [x] 근거 없는 설정값이 남아 있지 않은가
      HikariCP·Tomcat은 기본값 유지(Phase 3에서 측정 후 조정). `jwt.expiration-ms: 3600000`은 Phase 0 임시값임을 주석에 명시

### 발견한 문제

Phase 0 기준점이므로 **지금 고치지 않는다.** 해당 Phase에서 처리하고 그때 이 목록을 다시 확인한다.

| 문제 | 처리 Phase |
|---|---|
| `jwt.secret`이 평문으로 저장소에 커밋됨 | 2 (JWT 하드닝) |
| 쿠키에 HttpOnly·Secure·SameSite 없음 | 2 |
| 비밀번호 평문 저장 + 목록 조회 응답에 노출 | 2 |
| `PUT`/`DELETE`에 본인 확인 없음 (BOLA) | 2 |
| 가입 시 `customerPoint`를 클라이언트가 지정 가능 (Mass Assignment) | 2 |
| `quantity` 음수 검증 없음 — 주문 시 포인트가 늘어남 | 2 |
| 비즈니스 예외가 전부 HTTP 200 | 2 (Error → 상태 매핑) |
| 고객 삭제 시 주문이 남아 있으면 FK 제약 위반 → 500 | 2 (참조 무결성 정책) |
| 취소 환불이 주문 시점이 아닌 **현재 가격** 기준 | 1 (`orderedPrice` 스냅샷) |
| 고객 주문 조회에서 `item.getProduct()`마다 SELECT (N+1) | 3 |
| 동시 주문 시 포인트 Lost Update 가능 | 3 (`@Version`) |
| 테스트 코드 없음 | 4 |
