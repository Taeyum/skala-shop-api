// 동시 주문 정합성 — 서로 다른 고객이 동시에 주문할 때 포인트가 정확히 맞는가.
//
// Phase 3의 동시성 테스트는 **한 고객**에 100건을 몰아 락 충돌을 봤다.
// 여기서는 **여러 고객**이 각자 주문하는, 실제 트래픽에 가까운 형태를 본다.
// 이 경우 행 경합이 드물어야 하고, 따라서 충돌이 거의 없어야 한다 — 그 가정을 확인한다.
import { check } from 'k6';
import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { BASE, createCustomer, createProduct, placeOrder, authHeaders, json } from './lib.js';

const UNIT_PRICE = 1000;
const INITIAL_POINT = 1000000;
const ordersPlaced = new Counter('orders_placed');
const orderConflicts = new Counter('order_conflicts');

export const options = {
  scenarios: {
    // ★ shared-iterations 로 총량을 고정한다.
    // 처음에 constant-vus 30초로 돌렸더니 초당 8,000건이 나가 **고객 포인트가 소진**됐고
    // 응답의 79%가 INSUFFICIENT_FUNDS(400)였다. 정합성은 그래도 지켜졌지만
    // (50명 × 1,000건 = 잔액 정확히 0, 불일치 0) 측정하려던 '동시 주문'이 아니라
    // '잔액 소진'을 재고 있었다. 고객당 400건으로 묶어 전 구간이 성공하게 한다
    orders: {
      executor: 'shared-iterations', vus: 50,
      iterations: 20000, maxDuration: '3m', exec: 'order',
    },
  },
  thresholds: { checks: ['rate>0.99'] },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const productId = createProduct(`int-p-${Date.now()}`, UNIT_PRICE);
  const customers = [];
  for (let i = 0; i < 50; i++) customers.push(createCustomer(`int-c-${i}-${Date.now()}`));
  return { productId, customers };
}

export function order(data) {
  const customer = data.customers[(__VU - 1) % data.customers.length];
  const res = placeOrder(customer, data.productId, 1);
  if (res.status === 200) ordersPlaced.add(1);
  else if (res.status === 409) orderConflicts.add(1);
}

/**
 * 고객마다 "차감된 포인트 == 보유 수량 × 단가"인지 확인한다.
 * <b>총합이 아니라 고객별로 본다</b> — 총합만 맞고 개별이 어긋나는 경우를 놓치지 않기 위해서다.
 */
export function teardown(data) {
  let mismatched = 0;
  let totalQuantity = 0;
  for (const customer of data.customers) {
    const res = http.get(`${BASE}/api/customers/${customer.id}`, authHeaders(customer));
    const body = json(res);
    if (body === null || !body.body) { mismatched++; continue; }
    const quantity = body.body.products.reduce((sum, p) => sum + p.quantity, 0);
    const expected = INITIAL_POINT - quantity * UNIT_PRICE;
    totalQuantity += quantity;
    if (Number(body.body.customerPoint) !== expected) {
      console.error(`정합성 위반 ${customer.id}: 포인트 ${body.body.customerPoint}, 기대 ${expected}`);
      mismatched++;
    }
  }
  console.log(`[정합성] 고객 ${data.customers.length}명, 총 주문 수량 ${totalQuantity}, 불일치 ${mismatched}`);
  // ★ 불일치 0 만으로는 부족하다 — 주문이 실제로 일어났는지 함께 본다.
  //   주문이 0건이면 불일치도 0이고 "정합성 통과"로 보인다
  check(null, {
    '정합성 — 불일치 0': () => mismatched === 0,
    '정합성 — 주문이 실제로 일어났다': () => totalQuantity > 0,
  });
}
