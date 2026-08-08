// 캐시·ETag 전용 시나리오 — `GET /api/products/{id}` 만 때린다.
//
// ★ 왜 load.js 로는 잴 수 없는가
//   load.js 는 listProducts() · getOrders() · placeOrder() 만 호출한다.
//   **캐시가 걸린 상품 상세(`/api/products/{id}`)를 한 번도 부르지 않는다.**
//   그걸 모르고 캐시 on/off 를 load.js 로 재서 "차이 0.3%"라는 무의미한 결과를 얻었다
//   (2026-08-08, 폐기). 측정 대상이 실행되는지 먼저 확인해야 한다.
//
// ★ 이 시나리오는 **캐시 이득의 상한**을 잰다.
//   트래픽 100% 가 캐시 대상 경로다. 실제 트래픽 형태(load.js)에는 이 경로가 없으므로,
//   여기서 나온 이득은 **현실에서 얻을 수 있는 최대치**이고 실제 이득은 그보다 작다.
//   여기서도 이득이 없으면 어떤 트래픽에서도 없다.
//
// MODE=plain  — 매번 정상 GET (서버 부하 = @Cacheable 의 효과를 본다)
// MODE=etag   — If-None-Match 를 붙여 조건부 요청 (전송량 = ETag 의 효과를 본다)
//               ETag 와 @Cacheable 은 **다른 것을 줄인다**(DECISIONS 25절).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE, createProduct, countStatus } from './lib.js';

const MODE = __ENV.MODE || 'plain';

// 요청 사이 대기. 기본 0.1초는 "사용자가 화면을 보는 시간"을 흉내낸 것인데,
// ★ 그 상태에서는 서버가 포화되지 않아(327 req/s, 용량 1,000+) 서버측 개선이 드러나지 않는다.
//   THINK=0 으로 포화시켜 **캐시가 가장 크게 작동할 조건**을 따로 잰다.
const THINK = Number(__ENV.THINK !== undefined ? __ENV.THINK : 0.1);

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus', vus: 5, duration: '20s',
      startTime: '0s', tags: { phase: 'warmup' }, exec: 'traffic',
    },
    measure: {
      executor: 'constant-vus', vus: Number(__ENV.VUS || 50), duration: __ENV.DURATION || '45s',
      startTime: '25s', tags: { phase: 'measure' }, exec: 'traffic',
    },
  },
  thresholds: {
    'checks{phase:measure}': ['rate>0.99'],
    'http_req_failed{phase:measure}': ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const products = [];
  for (let i = 0; i < 20; i++) {
    products.push(createProduct(`cache-p-${i}-${Date.now()}`, 1000));
  }
  // etag 모드는 각 상품의 ETag 를 미리 받아둔다.
  // ★ 여기서 ETag 가 실제로 오는지 확인한다 — 안 오면 조건부 요청이 성립하지 않고
  //   "전송량이 안 줄었다"가 ETag 의 실패가 아니라 측정의 실패가 된다.
  const etags = {};
  if (MODE === 'etag') {
    for (const id of products) {
      const r = http.get(`${BASE}/api/products/${id}`);
      const tag = r.headers['Etag'] || r.headers['ETag'];
      if (!tag) {
        throw new Error(`ETag 가 응답에 없다 (product ${id}) — 측정이 성립하지 않는다`);
      }
      etags[id] = tag;
    }
  }
  return { products, etags };
}

export function traffic(data) {
  const id = data.products[__ITER % data.products.length];
  const params = { tags: { name: 'detail' } };

  if (MODE === 'etag') {
    params.headers = { 'If-None-Match': data.etags[id] };
  }

  const res = http.get(`${BASE}/api/products/${id}`, params);
  countStatus(res);

  if (MODE === 'etag') {
    // 304 면 본문이 없어야 한다. 200 이 오면 조건부 요청이 동작하지 않은 것이다
    check(res, {
      '304 또는 200': (r) => r.status === 304 || r.status === 200,
      '304 이면 본문 없음': (r) => r.status !== 304 || r.body === null || r.body.length === 0,
    });
  } else {
    // ★ 상태 코드만 보지 않는다 — 빈 응답도 200 이다 (lib.js 설계 원칙)
    check(res, {
      '200': (r) => r.status === 200,
      '상품 가격이 실려 있다': (r) => {
        try { return JSON.parse(r.body).body.productPrice > 0; } catch (e) { return false; }
      },
    });
  }

  if (THINK > 0) sleep(THINK);
}
