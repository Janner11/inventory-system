import http from 'k6/http';
import { check, sleep } from 'k6';
import { authHeaders } from './helpers/auth.js';
import { postWithRetryOn409 } from './helpers/retry.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// Ramping VU 10→50→100→200 (TEST-006) para encontrar el punto de quiebre del backend.
// A diferencia de load-test.js (3 escenarios paralelos con VUs propios), acá se usa UN
// solo escenario que alterna operaciones dentro de la misma iteración — así el número de
// VU en cada etapa coincide exactamente con lo pedido por el ticket (10/50/100/200), en
// vez de triplicarse al repartirlo entre 3 escenarios concurrentes.
//
// Es normal y esperado que los thresholds fallen cerca del pico de 200 VU — ese es
// justamente el propósito de un stress test: encontrar el punto en el que el sistema deja
// de cumplir sus objetivos de rendimiento, no exigir que los cumpla bajo cualquier carga.
export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 10 },
        { duration: '2m', target: 50 },
        { duration: '2m', target: 100 },
        { duration: '2m', target: 200 },
        { duration: '1m', target: 0 }, // ramp-down para observar recuperación
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>100'],
  },
};

export function setup() {
  const headers = authHeaders();
  const sku = `PERF-STRESS-SEED-${Date.now()}`;
  const res = http.post(
    `${BASE_URL}/api/products`,
    JSON.stringify({
      name: 'Producto Semilla — Stress Testing',
      sku,
      description: 'Creado por tests/performance/stress-test.js (setup) — no borrar durante el test',
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

function getProducts() {
  const headers = authHeaders();
  const res = http.get(`${BASE_URL}/api/products?page=0&size=20`, {
    headers,
    tags: { name: 'GetProducts' },
  });
  check(res, { 'getProducts: 200': (r) => r.status === 200 });
}

function createProduct() {
  const headers = authHeaders();
  const sku = `PERF-STRESS-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    name: 'Producto Stress Test',
    sku,
    description: 'Creado por tests/performance/stress-test.js',
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
}

function registerEntry(seedProductId) {
  const headers = authHeaders();
  const payload = JSON.stringify({
    productId: seedProductId,
    quantity: 1,
    reason: 'k6 stress-test entry',
    observations: 'Generado automáticamente por tests/performance/stress-test.js',
    performedBy: 'k6-stress-test',
  });
  const res = postWithRetryOn409(`${BASE_URL}/api/stock/entry`, payload, {
    headers,
    tags: { name: 'RegisterEntry' },
  });
  check(res, { 'registerEntry: 201': (r) => r.status === 201 });
}

/** Mezcla ponderada: ~70% lecturas, ~20% creación de productos, ~10% movimientos de stock. */
export default function (data) {
  const roll = Math.random();
  if (roll < 0.7) {
    getProducts();
  } else if (roll < 0.9) {
    createProduct();
  } else {
    registerEntry(data.seedProductId);
  }
  sleep(0.3);
}
