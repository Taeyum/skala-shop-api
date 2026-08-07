# N+1 — `GET /api/customers/{customerId}` 개선 전 실측

측정 시점: 2026-08-07 · 대조군 커밋: `phase2-security`
측정 방법: 실행 중인 앱의 `org.hibernate.SQL` DEBUG 로그에서 **요청 하나가 유발한 SQL 수**를 센다.

> 이 문서는 **로그 기반 1회성 캡처**다. 회귀를 계속 막는 영구 형태는
> 쿼리 카운트 테스트(`OrderQueryCountTest`)다 — 로그는 사람이 보고 잊지만 테스트는 깨진다.

---

## 측정 전에 확인한 것 — 측정 장치가 실제로 일하는가

측정값이 나왔다고 측정이 된 것은 아니다. 수치를 신뢰하기 전에 세 가지를 먼저 확인했다.

### ① 픽스처가 N+1을 재현할 수 있는가 (1차 캐시)

**같은 상품을 반복 주문하면 재현되지 않는다.** 첫 조회 이후 그 `Product`가 영속성 컨텍스트
1차 캐시에 올라가 두 번째부터는 SQL이 나가지 않기 때문이다. 그래서 픽스처를
**서로 다른 상품 20종, 각 1개씩 주문**으로 구성했다.

### ② 쿼리 수가 상품 수를 따라가는가

상수가 아니라 N에 비례해야 N+1이다.

| 상품 종수 N | 쿼리 수 |
|---|---|
| 5 | 7 |
| 10 | 12 |
| 20 | 22 |

**정확히 N+2.** 측정 장치가 N을 따라간다는 것이 확인된다.

### ③ 조회가 실패해도 "쿼리 1개"로 보이지 않는가 ★

가장 중요한 확인이다. Phase 2에서 이 엔드포인트에 인증을 걸었으므로(B-3),
**쿠키 없이 호출하면 401로 튕기고 쿼리는 0개**가 된다.

```
$ curl -s http://localhost:8080/api/customers/nobody      # 쿠키 없음
{"result":"fail","message":"NOT_AUTHENTICATED","body":null}
  쿼리 : 0 개
```

행 수를 단언하지 않는 측정이었다면 이 0이 **"N+1이 완벽히 해결됨"으로 읽힌다.**
그래서 측정 스크립트는 **쿼리를 세기 전에 반환된 행 수를 먼저 단언**하고,
기대와 다르면 수치를 출력하지 않고 중단한다.

```
✗ 응답 파싱 실패 (401로 튕겼을 수 있다): {"result":"fail",...}
```

> Phase 1까지는 "결과가 비어야만" 걸리는 함정이었다. Phase 2가 인증을 추가하면서
> **인증 누락만으로도 완벽한 그래프가 나오는** 상태가 됐다. 보안 강화가 측정의 위험을
> 키운 사례다 (`PLAN.md` Phase 3 주의 ②).

---

## 개선 전 측정 결과

픽스처: 고객 1명 · 서로 다른 상품 20종 · 각 1개씩 주문 (총 20 `OrderItem`)

```
✓ 반환 행 수 20 건 (= 주문한 상품 수)
GET /api/customers/{id}   쿼리 22 개   [상품 20 종]
✓ 대조군 반환 행 수 10 건
GET /api/products/list    쿼리 2 개    (대조군)
```

### 쿼리 종류별 집계 (요청 1건)

```
총 22 개
   20회  select p1_0.id, p1_0.product_name, p1_0.product_price from products p1_0 where p1_0.id=?
    1회  select c1_0.id, c1_0.customer_id, ... from customers c1_0 where c1_0.customer_id=?
    1회  select oi1_0.id, oi1_0.customer_id, oi1_0.ordered_amount, oi1_0.product_id, oi1_0.quantity
          from order_items oi1_0 where oi1_0.customer_id=?
```

**전형적인 N+1이다.** `OrderItem` 목록을 1번에 가져온 뒤(`1`),
`item.getProduct().getProductName()`이 호출될 때마다 상품을 개별 조회한다(`N=20`).
발생 지점은 `OrderService.getCustomerOrders()` — 앵커 주석이 달려 있던 자리다.

### 대조군

`GET /api/products/list`는 2개(count + list)이며 N+1과 무관하다.
**개선 후에도 이 값이 변하지 않아야** 변화의 출처가 fetch 전략임이 확정된다.

---

## 재현 절차

```bash
# 1. 앱을 로그 파일로 리다이렉트해 기동
./gradlew bootRun > app.log 2>&1 &

# 2. 고객 가입·로그인 후, 서로 다른 상품 20종을 만들고 각 1개씩 주문
#    (data.sql 시드 3종은 건드리지 않는다 — SPEC 5절 E2E가 "3개"를 단언한다)

# 3. 조회 직전 로그 위치를 기록하고 요청한 뒤, 늘어난 SQL 라인을 센다
MARK=$(wc -l < app.log)
curl -s -b cookie.txt http://localhost:8080/api/customers/$U
tail -n +$((MARK+1)) app.log | grep -c 'org.hibernate.SQL'
```

> **주의** — 로거 이름은 `org.hibernate.SQL`로 **축약되지 않는다.**
> 실제로 이 집계에서 `o.h.SQL`(Spring Boot 축약형)로 grep했다가 0을 받았다.
> 0이 명백히 틀린 값이라 바로 잡혔지만, 만약 "개선 후" 집계에서 같은 실수를 했다면
> **완벽한 개선으로 보였을 것이다** — ②③에서 막으려던 것과 정확히 같은 형태의 실패다.
