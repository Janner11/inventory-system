import http from 'k6/http';
import { check, sleep } from 'k6';
import { authHeaders } from './helpers/auth.js';
import { postWithRetryOn409 } from './helpers/retry.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// 50 VU constantes por 30 minutos (TEST-006, "en local" — deliberadamente NO forma parte
// de performance-test.yml/CI: 30 minutos por corrida es demasiado costoso para un pipeline
// que corre en cada PR, y el objetivo de un soak test — detectar degradación sostenida,
// fugas de memoria en la JVM, agotamiento del pool de conexiones — se observa mejor de
// forma manual con Grafana abierto en paralelo que leyendo un artifact de CI al final).
//
// Mientras corre, observar el dashboard "Inventario Backend" (OBS-004,
// http://localhost:3000/d/inventario-backend) — en particular los paneles "JVM Heap Used"
// y "HikariCP Connections": un heap que crece de forma sostenida sin que el GC lo baje de
// vuelta a un piso estable, o un pool de conexiones que se agota progresivamente, son las
// señales concretas de fuga que este test busca exponer (ver "Casos de Error" del ticket).
export const options = {
  scenarios: {
    soak: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30m',
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
  const sku = `PERF-SOAK-SEED-${Date.now()}`;
  const res = http.post(
    `${BASE_URL}/api/products`,
    JSON.stringify({
      name: 'Producto Semilla — Soak Testing',
      sku,
      description: 'Creado por tests/performance/soak-test.js (setup) — no borrar durante el test',
      category: 'Performance',
      price: 10,
      quantity: 10000000,
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
  const sku = `PERF-SOAK-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    name: 'Producto Soak Test',
    sku,
    description: 'Creado por tests/performance/soak-test.js',
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
    reason: 'k6 soak-test entry',
    observations: 'Generado automáticamente por tests/performance/soak-test.js',
    performedBy: 'k6-soak-test',
  });
  const res = postWithRetryOn409(`${BASE_URL}/api/stock/entry`, payload, {
    headers,
    tags: { name: 'RegisterEntry' },
  });
  check(res, { 'registerEntry: 201': (r) => r.status === 201 });
}

/** Misma mezcla ponderada que stress-test.js: ~70% lecturas, ~20% creación, ~10% stock. */
export default function (data) {
  const roll = Math.random();
  if (roll < 0.7) {
    getProducts();
  } else if (roll < 0.9) {
    createProduct();
  } else {
    registerEntry(data.seedProductId);
  }
  sleep(0.5);
}
