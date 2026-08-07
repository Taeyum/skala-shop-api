// k6 공용 헬퍼.
//
// 설계 원칙 — **성공 여부를 상태 코드만으로 판단하지 않는다.**
// Phase 3에서 "쿼리 0개"가 개선이 아니라 401이었고, Phase 5에서 "비밀번호 0건"이
// 방어가 아니라 빈 로그였다. 둘 다 부정 단언만 본 결과다.
// 여기서는 매 요청에서 **응답 바디의 내용까지** 확인한다.
import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';

// ★ 상태 코드 분포를 직접 센다.
// k6의 http_req_failed 는 4xx·5xx 를 함께 '실패'로 묶는데, 이 API에서 409(낙관적 락 충돌)는
// **정상 동작**이다. 묶어 놓으면 "실패율 0.26%"가 결함인지 정상인지 구분할 수 없다.
// 실제로 첫 스트레스 측정에서 checks 100% 인데 http_req_failed 0.26% 인 불일치를 만났다.
export const status2xx = new Counter('resp_2xx');
export const status4xx = new Counter('resp_4xx');
export const status409 = new Counter('resp_409_conflict');
export const status5xx = new Counter('resp_5xx');
// 상태 0 = 연결 실패·타임아웃. 어느 상태 코드 분류에도 안 들어가므로 따로 센다.
// 첫 스트레스 측정에서 http_req_failed 0.04%가 어느 분류에도 잡히지 않아 추가했다
export const statusZero = new Counter('resp_conn_error');
export const setupReqs = new Counter('setup_requests');
// k6가 error_code 를 붙인 응답 수. **HTTP 4xx·5xx 도 여기 포함된다** —
// 이름이 'transport'가 아니라는 것을 실측으로 확인했다: 409 개수와 정확히 일치했다.
// http_req_failed 와 이 값이 같으면 '실패'의 정체가 상태 코드임을 뜻한다
export const errorCoded = new Counter('resp_error_coded');

export function countStatus(res) {
  if (res.error_code !== 0) {
    errorCoded.add(1);
  }
  // 연결 자체가 실패하면 status 0 — 아래 분류 어디에도 안 들어가므로 따로 센다
  if (res.status === 0) {
    statusZero.add(1);
    console.warn(`연결 실패: code=${res.error_code} error="${res.error}"`);
  }
  if (res.status >= 200 && res.status < 300) status2xx.add(1);
  else if (res.status === 409) status409.add(1);
  else if (res.status >= 400 && res.status < 500) status4xx.add(1);
  else if (res.status >= 500) status5xx.add(1);
}

export const BASE = __ENV.BASE || 'http://app:8080';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function json(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

/** 가입 → 로그인 → 쿠키 문자열 반환. setup()에서만 쓴다(측정 구간 밖) */
export function createCustomer(id, password = 'pw1234') {
  const body = JSON.stringify({ customerId: id, customerPassword: password });
  const reg = http.post(`${BASE}/api/customers`, body, { headers: JSON_HEADERS, tags: { name: 'setup' } });
  setupReqs.add(1);
  countStatus(reg);
  if (reg.status !== 200) {
    fail(`가입 실패 ${id}: ${reg.status} ${reg.body}`);
  }
  const login = http.post(`${BASE}/api/customers/login`, body, { headers: JSON_HEADERS, tags: { name: 'setup' } });
  setupReqs.add(1);
  countStatus(login);
  if (login.status !== 200) {
    fail(`로그인 실패 ${id}: ${login.status} ${login.body}`);
  }
  const setCookie = login.headers['Set-Cookie'];
  const token = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
  return { id, cookie: `bff-access=${token}` };
}

export function createProduct(name, price) {
  const res = http.post(`${BASE}/api/products`,
    JSON.stringify({ productName: name, productPrice: price }),
    { headers: JSON_HEADERS, tags: { name: 'setup' } });
  setupReqs.add(1);
  countStatus(res);
  if (res.status !== 200) {
    fail(`상품 생성 실패 ${name}: ${res.status} ${res.body}`);
  }
  return json(res).body.id;
}

export function authHeaders(customer) {
  return { headers: { ...JSON_HEADERS, Cookie: customer.cookie } };
}

/**
 * 상품 목록 조회. **대조군으로 쓴다** — 인증도 주문도 없는 경로라
 * 튜닝 대상(커넥션 풀·캐시)의 영향을 제외한 나머지가 변했는지 알려준다.
 */
export function listProducts(tag = 'list') {
  const res = http.get(`${BASE}/api/products/list?offset=0&count=10`, { tags: { name: tag } });
  check(res, {
    'list 200': (r) => r.status === 200,
    // ★ 상태만 보지 않는다. 빈 목록이 와도 200이고 응답은 빠르다
    'list 비어있지 않음': (r) => {
      const b = json(r);
      return b !== null && b.body && Array.isArray(b.body.list) && b.body.list.length > 0;
    },
  });
  countStatus(res);
  return res;
}

/** 주문 조회 — 인증이 필요한 읽기 경로 */
export function getOrders(customer, expectAtLeast = 0) {
  const res = http.get(`${BASE}/api/customers/${customer.id}`,
    { ...authHeaders(customer), tags: { name: 'orders' } });
  check(res, {
    'orders 200': (r) => r.status === 200,
    // 401로 튕겨도 응답 시간은 그려진다. 인증이 살아 있는지 바디로 확인한다
    'orders 인증됨': (r) => {
      const b = json(r);
      return b !== null && b.body && b.body.customerId === customer.id;
    },
    'orders 최소 건수': (r) => {
      const b = json(r);
      return b !== null && b.body && b.body.products.length >= expectAtLeast;
    },
  });
  countStatus(res);
  return res;
}

/** 주문 — 쓰기 경로 */
export function placeOrder(customer, productId, quantity = 1) {
  const res = http.post(`${BASE}/api/customers/order`,
    JSON.stringify({ productId, quantity }),
    { ...authHeaders(customer), tags: { name: 'order' } });
  check(res, {
    'order 처리됨': (r) => r.status === 200 || r.status === 409 || r.status === 400,
  });
  countStatus(res);
  return res;
}
