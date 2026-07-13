import http from 'k6/http';
import { check, sleep } from 'k6';
import { authHeaders } from './helpers/auth.js';
import { postWithRetryOn409 } from './helpers/retry.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// Duración/VUs parametrizables vía -e (default: exactamente lo pedido por el ticket, 50 VU
// / 5 min) — permite correr una versión reducida para smoke-testing (`-e DURATION=15s
// -e VU_SCALE=0.1`) con el mismo script real, en vez de mantener una copia aparte.
const DURATION = __ENV.DURATION || '5m';
const VU_SCALE = Number(__ENV.VU_SCALE || 1);
const scaledVus = (n) => Math.max(1, Math.round(n * VU_SCALE));

// 50 VU por 5 minutos (TEST-006), repartidos entre 3 escenarios concurrentes que reflejan
// un uso realista de la API: lecturas (listar productos) muchas más frecuentes que
// escrituras (crear producto, registrar entrada de stock).
export const options = {
  scenarios: {
    getProducts: {
      executor: 'constant-vus',
      vus: scaledVus(25),
      duration: DURATION,
      exec: 'getProducts',
      tags: { scenario: 'getProducts' },
    },
    createProduct: {
      executor: 'constant-vus',
      vus: scaledVus(15),
      duration: DURATION,
      exec: 'createProduct',
      tags: { scenario: 'createProduct' },
    },
    registerEntry: {
      executor: 'constant-vus',
      vus: scaledVus(10),
      duration: DURATION,
      exec: 'registerEntry',
      tags: { scenario: 'registerEntry' },
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>100'],
  },
};

/**
 * setup() corre una sola vez, antes de que arranquen los VUs — se usa para crear un
 * producto "semilla" con stock alto, reutilizado por todas las iteraciones del escenario
 * registerEntry (evita tener que crear un producto nuevo en cada iteración solo para
 * poder registrarle una entrada, lo que mediría creación de productos dos veces).
 */
export function setup() {
  const headers = authHeaders();
  const sku = `PERF-SEED-${Date.now()}`;
  const res = http.post(
    `${BASE_URL}/api/products`,
    JSON.stringify({
      name: 'Producto Semilla — Performance Testing',
      sku,
      description: 'Creado por tests/performance/load-test.js (setup) — no borrar durante el test',
      category: 'Performance',
      price: 10,
      quantity: 1000000,
      minStock: 1,
    }),
    { headers, tags: { name: 'SetupSeedProduct' } },
  );

  check(res, { 'setup: producto semilla creado (201)': (r) => r.status === 201 });

  return { seedProductId: res.json('id') };
}

export function getProducts() {
  const headers = authHeaders();
  const res = http.get(`${BASE_URL}/api/products?page=0&size=20`, {
    headers,
    tags: { name: 'GetProducts' },
  });
  check(res, { 'getProducts: 200': (r) => r.status === 200 });
  sleep(0.3);
}

export function createProduct() {
  const headers = authHeaders();
  const sku = `PERF-LOAD-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    name: 'Producto Load Test',
    sku,
    description: 'Creado por tests/performance/load-test.js',
    category: 'Performance',
    price: 19.99,
    quantity: 10,
    minStock: 2,
  });
  const res = http.post(`${BASE_URL}/api/products`, payload, {
    headers,
    tags: { name: 'CreateProduct' },
  });
  check(res, { 'createProduct: 201': (r) => r.status === 201 });
  sleep(0.5);
}

export function registerEntry(data) {
  const headers = authHeaders();
  const payload = JSON.stringify({
    productId: data.seedProductId,
    quantity: 1,
    reason: 'k6 load-test entry',
    observations: 'Generado automáticamente por tests/performance/load-test.js',
    performedBy: 'k6-load-test',
  });
  const res = postWithRetryOn409(`${BASE_URL}/api/stock/entry`, payload, {
    headers,
    tags: { name: 'RegisterEntry' },
  });
  check(res, { 'registerEntry: 201': (r) => r.status === 201 });
  sleep(0.5);
}
