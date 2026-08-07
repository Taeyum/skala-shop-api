// 부하 테스트 — 목표 부하에서 p50/p95/p99와 TPS를 잰다.
//
// **워밍업 구간을 둔다.** JIT 컴파일과 커넥션 풀 초기화가 초반 수치를 크게 왜곡한다.
// 워밍업 구간의 지표는 `warmup` 태그로 분리해 본 측정에서 제외한다.
import { sleep } from 'k6';
import { createCustomer, createProduct, listProducts, getOrders, placeOrder } from './lib.js';

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus', vus: 5, duration: '20s',
      startTime: '0s', tags: { phase: 'warmup' }, exec: 'traffic',
    },
    measure: {
      executor: 'constant-vus', vus: Number(__ENV.VUS || 30), duration: __ENV.DURATION || '60s',
      startTime: '25s', tags: { phase: 'measure' }, exec: 'traffic',
    },
  },
  thresholds: {
    // ★ 성공률을 먼저 건다. 이것이 깨지면 응답 시간 수치는 무효다
    'checks{phase:measure}': ['rate>0.99'],
    'http_req_failed{phase:measure}': ['rate<0.01'],
    'http_req_duration{phase:measure}': ['p(95)<500'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const products = [];
  for (let i = 0; i < 20; i++) {
    products.push(createProduct(`load-p-${i}-${Date.now()}`, 1000));
  }
  const customers = [];
  for (let i = 0; i < 40; i++) {
    customers.push(createCustomer(`load-c-${i}-${Date.now()}`));
  }
  return { products, customers };
}

export function traffic(data) {
  const customer = data.customers[(__VU - 1) % data.customers.length];
  const productId = data.products[__ITER % data.products.length];

  // 읽기 3 : 쓰기 1 — 일반적인 쇼핑몰 트래픽 형태에 가깝게
  listProducts();
  getOrders(customer);
  listProducts();
  placeOrder(customer, productId, 1);

  sleep(0.1);
}
