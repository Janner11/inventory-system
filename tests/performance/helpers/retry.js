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
 */
const MAX_ATTEMPTS = 5;

export function postWithRetryOn409(url, payload, params) {
  let res;
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    res = http.post(url, payload, params);
    if (res.status !== 409) {
      return res;
    }
    // Backoff corto con jitter para desincronizar a los VUs que chocaron en el mismo instante.
    sleep(0.05 + Math.random() * 0.1);
  }
  return res;
}
