// 스트레스 — VU를 점진적으로 올려 한계점을 찾는다.
//
// **임계값을 abortOnFail로 걸지 않는다.** 한계를 찾는 것이 목적이므로 깨지는 지점까지 가야 한다.
// 대신 단계별 태그로 나눠 "어느 단계에서 성공률이 떨어졌는가"를 본다.
import { sleep } from 'k6';
import { createCustomer, createProduct, listProducts, getOrders, placeOrder } from './lib.js';

// 200 VU까지는 성공률 100%로 한계가 드러나지 않았다. 더 올려 실제 꺾이는 지점을 찾는다
const STAGES = [50, 100, 200, 400, 800];

export const options = {
  scenarios: Object.fromEntries(STAGES.map((vus, i) => [`s${vus}`, {
    executor: 'constant-vus', vus, duration: '30s',
    startTime: `${20 + i * 35}s`, tags: { stage: `${vus}` }, exec: 'traffic',
  }]).concat([['warmup', {
    executor: 'constant-vus', vus: 5, duration: '15s', startTime: '0s',
    tags: { stage: 'warmup' }, exec: 'traffic',
  }]])),
  thresholds: Object.assign(
    Object.fromEntries(STAGES.map((v) => [`checks{stage:${v}}`, ['rate>=0']])),
    // http_req_failed 를 엔드포인트별로 쪼개 어디서 나는지 본다 (rate<1 은 항상 통과 — 출력이 목적)
    {
      'http_req_failed{name:list}': ['rate<1'],
      'http_req_failed{name:orders}': ['rate<1'],
      'http_req_failed{name:order}': ['rate<1'],
      'http_req_failed{name:setup}': ['rate<1'],
    }),
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const products = [];
  for (let i = 0; i < 20; i++) products.push(createProduct(`st-p-${i}-${Date.now()}`, 100));
  const customers = [];
  for (let i = 0; i < 400; i++) customers.push(createCustomer(`st-c-${i}-${Date.now()}`));
  return { products, customers };
}

export function traffic(data) {
  const customer = data.customers[(__VU - 1) % data.customers.length];
  listProducts();
  getOrders(customer);
  placeOrder(customer, data.products[__ITER % data.products.length], 1);
  sleep(0.05);
}
