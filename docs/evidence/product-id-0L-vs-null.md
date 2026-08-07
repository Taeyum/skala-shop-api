# 신규 Product의 ID를 0L로 세팅하면 SELECT가 하나 늘어난다

강의 자료(인쇄 549)는 `createProduct`에서 **"신규 Product의 ID는 0L로 세팅(=JPA가 자동 생성)"** 하라고 한다.
따르지 않기로 한 판단의 근거 — 추측이 아니라 실측이다.

## 측정 조건

| 항목 | 값 |
|---|---|
| 측정일 | 2026-08-07 |
| DB | PostgreSQL 16.14 (docker compose, 호스트 5433) |
| 엔티티 | `Product.id` = `Long` (래퍼) + `@GeneratedValue(strategy = IDENTITY)` |
| 요청 | `POST /api/products` `{"productName":"증거수집상품","productPrice":1234}` |
| 로그 | `logging.level.org.hibernate.SQL: debug`, `format_sql: true` |
| 차이 | `productRepository.save(product)` 직전에 `product.setId(0L)` 유무. **그 외 코드 동일** |

`select ... where product_name=?`는 두 경우 모두 나오는 **이름 중복 체크**(`existsByProductName`)로, 비교 대상이 아니다.

## A. 현재 구현 — `id`를 건드리지 않음 (null)

쿼리 **2회**.

```sql
-- 1) 이름 중복 체크 (비즈니스 로직)
select p1_0.id
from products p1_0
where p1_0.product_name=?
fetch first ? rows only

-- 2) 저장
insert into products (product_name, product_price)
values (?, ?)
returning id
```

## B. 자료대로 `product.setId(0L)`

쿼리 **3회**. 가운데 SELECT가 새로 생긴다.

```sql
-- 1) 이름 중복 체크 (동일)
select p1_0.id
from products p1_0
where p1_0.product_name=?
fetch first ? rows only

-- 2) ★ 추가된 조회 — merge가 기존 행을 찾으려 한다
select p1_0.id,
       p1_0.product_name,
       p1_0.product_price
from products p1_0
where p1_0.id=?

-- 3) 저장 (결과는 동일)
insert into products (product_name, product_price)
values (?, ?)
returning id
```

## 원인

Spring Data JPA의 `SimpleJpaRepository.save()`는 이렇게 갈린다.

```java
if (entityInformation.isNew(entity)) {
    em.persist(entity);      // INSERT 한 번
} else {
    em.merge(entity);        // SELECT 후 INSERT/UPDATE
}
```

`isNew()`는 **id가 래퍼 타입이면 `id == null`로 판정**한다.
`0L`은 null이 아니므로 "이미 존재하는 엔티티"로 분류되고, `merge()`가 id=0인 행을 찾으러 SELECT를 날린다.
찾지 못하면 결국 새로 INSERT하므로 **결과는 같고 쿼리만 하나 더 나간다.**

## 판단

자료의 지침은 id 필드가 원시형 `long`이던 시절의 관용구로 보인다.
원시형은 기본값이 `0`이라 null 판정이 불가능해 `isNew()`를 `id == 0`으로 다루는 구현이 필요했고,
그 맥락에서 "0으로 세팅"이 의미를 가졌다.

현재 구조(`Long` + IDENTITY)에서는 **아무것도 얻지 못하고 SELECT만 늘어난다.**
상품 등록 1건당 쿼리 2회 → 3회, 즉 **50% 증가**다. 대량 등록에서는 그대로 누적된다.

따라서 구현하지 않는다. `id`는 null로 두어 `persist` 경로를 탄다.
