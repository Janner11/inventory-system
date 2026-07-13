import http from 'k6/http';
import { sleep } from 'k6';

/**
 * registerEntry (y cualquier escritura contra un recurso "caliente" compartido por muchos
 * VUs) puede chocar con el optimistic locking de `Product` (@Version, BACK-002) cuando
 * varias entradas de stock concurrentes intentan actualizar la misma fila — el backend
 * responde 409 con el mensaje "El producto fue modificado por otro proceso, recargue e
 * intente de nuevo". Un cliente real reintentaría en vez de tratarlo como un error fatal
 * (es literalmente lo que dice el mensaje); sin retry, un load test con muchos VUs
 * escribiendo sobre el mismo producto reporta una tasa de error alta que no refleja un
 * defecto del backend, sino contención esperada en un recurso compartido.
 *
 * Verificado empíricamente: con 10 VU escribiendo sobre un único producto semilla (sleep
 * de 0.5s entre iteraciones), ~29% de los POST /api/stock/entry chocaban con 409 sin retry.
 *
 * Nota sobre la métrica http_req_failed: k6 registra CADA llamada individual a
 * http.post() como su propia entrada en http_req_failed/http_req_duration, incluyendo los
 * intentos que chocaron con 409 y fueron reintentados con éxito después — sin
 * expectedStatuses, esos intentos "esperados y manejados" inflan http_req_failed muy por
 * encima de la tasa real de fallo (encontrado real en CI, corriendo en un runner de
 * GitHub Actions más lento/con más contención que la máquina de verificación local:
 * 4.06% reportado en http_req_failed, pero solo ~0.01% de las iteraciones nunca tuvieron
 * éxito tras agotar los 5 intentos — el propio check "registerEntry: 201" en
 * load-test.js ya mide esa tasa real por separado). `http.expectedStatuses(...)` le dice
 * a k6 que 409 en este endpoint es un resultado esperado (documentado en el mensaje del
 * propio backend, "recargue e intente de nuevo"), no un error de infraestructura o un
 * defecto — el fallo lógico real (la iteración completa nunca tuvo éxito) lo sigue
 * capturando el check() de cada escenario, que no se ve afectado por esto.
 */
const MAX_ATTEMPTS = 5;
const EXPECTED_STATUSES = http.expectedStatuses(200, 201, 409);

export function postWithRetryOn409(url, payload, params) {
  const requestParams = { ...params, responseCallback: EXPECTED_STATUSES };
  let res;
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    res = http.post(url, payload, requestParams);
    if (res.status !== 409) {
      return res;
    }
    // Backoff corto con jitter para desincronizar a los VUs que chocaron en el mismo instante.
    sleep(0.05 + Math.random() * 0.1);
  }
  return res;
}
