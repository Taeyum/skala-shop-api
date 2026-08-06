# API 계약 (변경 금지)

강의 자료에서 확정된 스펙. **URI, HTTP 메서드, 요청/응답 JSON 필드명은 절대 바꾸지 않는다.**
내부 구현(엔티티 필드, 패키지, 로직)은 개선 대상이며 그 근거는 `DECISIONS.md`에 기록한다.

---

## 1. 엔드포인트

### 상품 `/api/products`

| Method | URI | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/products/list?offset=0&count=10` | 전체 목록 (페이징) | 불필요 |
| GET | `/api/products/{id}` | 개별 상세 | 불필요 |
| POST | `/api/products` | 등록 | 불필요 |
| PUT | `/api/products` | 수정 (body에 id 포함) | 불필요 |
| DELETE | `/api/products` | 삭제 (body에 id 포함) | 불필요 |

### 고객 `/api/customers`

| Method | URI | 설명 | 인증 |
|---|---|---|---|
| GET | `/api/customers/list?offset=0&count=10` | 전체 목록 (페이징) | 불필요 |
| GET | `/api/customers/{customerId}` | 고객 정보 + 주문 상품 목록 | 불필요 |
| POST | `/api/customers` | 회원가입 | 불필요 |
| POST | `/api/customers/login` | 로그인 → JWT 쿠키 발급 | 불필요 |
| PUT | `/api/customers` | 정보 수정 | **본인만** |
| DELETE | `/api/customers` | 탈퇴 | **본인만** |
| POST | `/api/customers/order` | 상품 주문 | **필수** |
| POST | `/api/customers/cancel` | 주문 취소 | **필수** |

> `PUT`/`DELETE`의 인증 요구는 스펙에 없으나 BOLA 취약점 방어를 위해 추가한 것 (`DECISIONS.md` 참조).
> URI와 요청 형식은 그대로 유지한다.

---

## 2. 요청 / 응답 형식

### 회원가입
```
POST /api/customers
{ "customerId": "skala01", "customerPassword": "pw1234" }
```
초기 포인트는 **서버가 고정값으로 부여**한다. 클라이언트가 `customerPoint`를 보내도 무시한다.

### 로그인
```
POST /api/customers/login
{ "customerId": "skala01", "customerPassword": "pw1234" }
→ Set-Cookie: bff-access=<JWT>
```
요청 바디는 `CustomerSession`(customerId, customerPassword)으로 받는다.
로그인 성공 시 고객 정보를 반환하되 **비밀번호는 제외한다.**

### 주문 / 취소
```
POST /api/customers/order
POST /api/customers/cancel
{ "productId": 1, "quantity": 2 }
```
`customerId`는 **바디로 받지 않는다.** JWT 쿠키에서 추출한다.

### 고객 주문 조회
```
GET /api/customers/skala01
→ { "customerId": "skala01", "customerPoint": 970000,
    "products": [ { "productId": 1, "productName": "무선마우스",
                    "productPrice": 15000, "quantity": 2 } ] }
```

모든 응답은 공통 `Response` 객체로 감싼다.

---

## 3. 비즈니스 규칙

| 항목 | 규칙 |
|---|---|
| 주문 | 보유 포인트로 결제. 부족 시 `INSUFFICIENT_FUNDS` |
| 재주문 | 같은 상품 재주문 시 **수량 누적** (신규 행 생성 아님) |
| 취소 | 수량 차감, **0이 되면 OrderItem 삭제**, 포인트 환불 |
| 취소 초과 | 보유 수량보다 많이 취소 시 `INSUFFICIENT_QUANTITY` |
| 환불 금액 | **주문 당시 가격 기준** (`orderedPrice` 스냅샷 사용) |
| 수량 | 1 이상만 허용 (`@Positive`) |
| 트랜잭션 | 주문·취소는 `@Transactional`로 원자 처리 |
| 비밀번호 | 전 구간 응답에서 노출 금지 |

---

## 4. 에러 코드 → HTTP 상태

| Error | HTTP | 상황 |
|---|---|---|
| `DATA_NOT_FOUND` | 404 | 없는 상품·고객 |
| `DATA_DUPLICATED` | 409 | 중복 ID·상품명 |
| `INSUFFICIENT_FUNDS` | 400 | 포인트 부족 |
| `INSUFFICIENT_QUANTITY` | 400 | 취소 수량 초과 |
| `NOT_AUTHENTICATED` | 401 | 미로그인·토큰 무효 |
| (본인 불일치) | 403 | 남의 리소스 접근 |
| `ParameterException` | 400 | 필수값 누락·형식 오류 |
| 낙관적 락 충돌 | 409 | 동시 수정 |
| 예상 못한 예외 | 500 | 일반 메시지 + traceId만 노출 |

---

## 5. E2E 검증 시나리오 (Postman)

```
1) POST /api/customers        skala01 / pw1234        → 포인트 1,000,000
2) POST /api/customers/login                          → JWT 쿠키 수신
3) GET  /api/products/list                            → 3개 상품 확인
4) POST /api/customers/order  {productId:1, qty:2}    → 잔액 970,000
5) GET  /api/customers/skala01                        → 무선마우스 수량 2
6) POST /api/customers/cancel {productId:1, qty:1}    → 잔액 985,000
```

시드 데이터: 무선마우스 15,000 · 블루투스키보드 29,000 · USB허브 39,000
