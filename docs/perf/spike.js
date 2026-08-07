// 스파이크 — 평상시 부하에서 순간 폭증 후 회복되는지 본다.
// 보는 것은 최대 TPS가 아니라 **회복 여부**다: 폭증이 끝난 뒤 성공률과 응답시간이
// 평상시 수준으로 돌아오는가. 돌아오지 않으면 커넥션 풀 고갈이나 큐 적체를 의심한다.
import { sleep } from 'k6';
import { createCustomer, createProduct, listProducts, getOrders } from './lib.js';

export const options = {
  scenarios: {
    baseline: {
      executor: 'constant-vus', vus: 10, duration: '90s', startTime: '0s',
      tags: { window: 'baseline' }, exec: 'traffic',
    },
    spike: {
      executor: 'ramping-vus', startTime: '30s', startVUs: 0,
      stages: [
        { duration: '5s', target: 150 },   // 급격히 올린다
        { duration: '15s', target: 150 },
        { duration: '5s', target: 0 },
      ],
      tags: { window: 'spike' }, exec: 'traffic',
    },
  },
  thresholds: {
    'checks{window:baseline}': ['rate>0.95'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const products = [];
  for (let i = 0; i < 10; i++) products.push(createProduct(`sp-p-${i}-${Date.now()}`, 100));
  const customers = [];
  for (let i = 0; i < 160; i++) customers.push(createCustomer(`sp-c-${i}-${Date.now()}`));
  return { products, customers };
}

export function traffic(data) {
  const customer = data.customers[(__VU - 1) % data.customers.length];
  listProducts();
  getOrders(customer);
  sleep(0.1);
}
