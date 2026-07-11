# Performance Testing (k6) — TEST-006

Suite de pruebas de rendimiento del backend con [k6](https://k6.io/), corriendo contra el
stack de `docker-compose.dev.yml` (Postgres + Keycloak + backend — `docker-compose.staging.yml`,
INFRA-004, todavía no existe; ver la nota de adaptación en cada ticket de testing anterior).

## Scripts

| Script | Carga | Duración | Dónde corre |
|--------|-------|----------|-------------|
| `tests/performance/load-test.js` | 50 VU (25 lectura / 15 creación / 10 stock) | 5 min | CI (`performance-test.yml`) + local |
| `tests/performance/stress-test.js` | Ramping 10→50→100→200 VU | 8 min | Local (manual) |
| `tests/performance/soak-test.js` | 50 VU constantes | 30 min | Local (manual) — deliberadamente fuera de CI |

Los 3 comparten `helpers/auth.js` (login/refresh de token Keycloak por VU — el access token
del realm `inventario` vive 300s, así que un load test de 5 min ya lo roza) y
`helpers/retry.js` (reintento con backoff sobre 409 de optimistic locking — ver "Hallazgo:
contención en `registerEntry`" abajo).

## Cómo correrlos

```bash
# Stack real corriendo (docker-compose.dev.yml)
docker compose -f docker-compose.dev.yml up -d postgres keycloak backend

# Load test (usa la imagen oficial de k6, no requiere instalación local)
docker run --rm --network inventario-network \
  -v "$(pwd)/tests/performance:/scripts:rw" \
  -e KEYCLOAK_URL=http://keycloak:8080 \
  -e BASE_URL=http://backend:8081 \
  grafana/k6 run --summary-export=/scripts/results/load-test-summary.json /scripts/load-test.js

# Stress test (mismo patrón, ~8 min)
docker run --rm --network inventario-network \
  -v "$(pwd)/tests/performance:/scripts:rw" \
  -e KEYCLOAK_URL=http://keycloak:8080 -e BASE_URL=http://backend:8081 \
  grafana/k6 run --summary-export=/scripts/results/stress-test-summary.json /scripts/stress-test.js

# Soak test (30 min — dejar Grafana abierto en paralelo, ver sección "Soak test" abajo)
docker run --rm --network inventario-network \
  -v "$(pwd)/tests/performance:/scripts:rw" \
  -e KEYCLOAK_URL=http://keycloak:8080 -e BASE_URL=http://backend:8081 \
  grafana/k6 run --summary-export=/scripts/results/soak-test-summary.json /scripts/soak-test.js
```

`load-test.js` acepta `-e DURATION=15s -e VU_SCALE=0.1` para correr una versión reducida
(smoke test) del mismo script real, útil para verificar que todo funciona sin esperar 5
minutos ni generar miles de productos de prueba.

## Thresholds (los 3 scripts)

```
http_req_duration: p(95) < 500ms
http_req_failed:   rate < 1%
http_reqs:         rate > 100/s
```

## Resultados — Load Test (50 VU, 5 min)

**Ejecutado 2026-07-11 contra el stack local real** (`docker-compose.dev.yml`, backend sin
modificar — build de producción `./gradlew build -x test`).

| Threshold | Resultado | Estado |
|-----------|-----------|--------|
| `p(95) < 500ms` | **19.55ms** | ✅ PASS (25× margen) |
| `http_req_failed < 1%` | **0.40%** | ✅ PASS |
| `http_reqs rate > 100/s` | **128.98 req/s** | ✅ PASS |

```
checks_succeeded: 100.00% (38,594 / 38,594)
http_req_duration: avg=11.93ms med=6.13ms p(90)=15.46ms p(95)=19.55ms max=3.65s
http_reqs: 38,799 requests en 5m00.8s (128.98 req/s)
iterations: 38,542 (128.13/s)
vus: 50 (constante, 25 getProducts / 15 createProduct / 10 registerEntry)
```

Los 3 thresholds pasan con margen amplio — a 50 VU el backend no muestra ningún signo de
estrés (heap JVM en patrón de diente de sierra saludable durante toda la corrida, sin
crecimiento sostenido; ver query de Prometheus más abajo).

## Resultados — Stress Test (ramping 10→50→100→200 VU, 8 min)

**Ejecutado 2026-07-11 contra el mismo stack, en una corrida separada** (no simultánea con
el load test ni el soak test, para no contaminar el punto de quiebre medido).

| Threshold | Resultado agregado | Estado |
|-----------|---------------------|--------|
| `p(95) < 500ms` | **606.82ms** | ❌ FAIL |
| `http_req_failed < 1%` | **2.54%** | ❌ FAIL |
| `http_reqs rate > 100/s` | **167.39 req/s** | ✅ PASS |

El agregado de toda la corrida ya no cumple los thresholds — **ese es justamente el
objetivo de un stress test**: encontrar el punto en el que el sistema deja de cumplir sus
objetivos de rendimiento, no exigir que los cumpla bajo cualquier carga. El dato interesante
no es el agregado sino **cuándo** durante la rampa empieza a degradar.

### Punto de quiebre (medido con las métricas del propio backend en Prometheus, `histogram_quantile` sobre `http_server_requests_seconds_bucket`, ventanas de 30s)

| Hora | Etapa de la rampa | p95 | Error rate | Throughput |
|------|--------------------|-----|-----------|------------|
| 17:57:30 | 10 VU | 13–61ms | ~0–1.3% | ~10–45 req/s |
| 17:58:30–18:00:30 | rampa a 50 VU | 12–20ms | ~0.8–1.4% | 26–120 req/s |
| 18:00:30–18:01:40 | rampa a 100 VU (temprano) | 14–17ms | ~0.8–0.9% | 152–187 req/s |
| **18:01:40** | **~100 VU — inicio de la degradación** | **93ms** | **2.02%** | 214 req/s |
| 18:02:10–18:02:40 | rampa a 100→200 VU | 162–182ms | 2.75–2.90% | 228–289 req/s (pico) |
| 18:03:10–18:03:40 | rampa a 200 VU | **420ms → 548ms** (cruza el threshold de 500ms) | 3.24–3.45% | 263–270 req/s |
| 18:04:10 | pico de 200 VU | **985ms** (peor momento) | 4.10% | 220 req/s |
| 18:04:30–18:05:30 | ramp-down a 0 | 877ms → 623ms | 3.10–2.44% | 248→230 req/s |

**Conclusión: el punto de quiebre está entre ~100 y ~150 VU concurrentes.** Hasta ahí el
backend sostiene p95 de dos dígitos de milisegundos; a partir de ahí la latencia crece de
forma pronunciada (93ms → 985ms) y el throughput deja de escalar con más VUs (se estanca
entre 220–290 req/s en vez de seguir subiendo), la firma clásica de un recurso saturado
aguas abajo del servidor HTTP.

### Causa raíz identificada: pool de conexiones HikariCP agotado, no la JVM

Se correlacionó la degradación con `hikaricp_connections_active` (Prometheus) durante la
misma ventana:

```
17:57:10–18:01:10   active=0  (o 1 puntual)
18:01:40             active=4
18:02:10–18:05:10    active=10   ← se queda clavado en 10 (el máximo configurado)
```

`backend/src/main/resources/application.yml`:

```yaml
hikari:
  maximum-pool-size: ${DB_POOL_MAX_SIZE:10}
```

El pool se satura exactamente en el mismo momento (~18:01:40–18:02:10) en que el p95
empieza a subir — coincide con el inicio de la rampa hacia 100 VU. Con el pool lleno,
las requests que necesitan una conexión a la base de datos se encolan esperando que se
libere una (explica el salto de latencia) en vez de fallar rápido; eso a su vez aumenta la
ventana de contención para el optimistic locking de `Product` (ver hallazgo siguiente),
elevando también la tasa de error.

Se descartó la JVM como causa: el heap (`jvm_memory_used_bytes`, área `heap`) osciló entre
70MB y 240MB durante todo el stress test, sin ninguna tendencia de crecimiento sostenido —
patrón de diente de sierra saludable (el GC libera memoria entre picos), no una fuga.

**Recomendación concreta** (no implementada en este ticket — es un ajuste de
configuración/capacidad, no un defecto de código; queda para una sesión de tuning si el
proyecto necesita sostener >100 VU reales): subir `DB_POOL_MAX_SIZE` por encima de 10 y
volver a correr `stress-test.js` para confirmar que el punto de quiebre se mueve más
arriba. HikariCP recomienda dimensionar el pool según `((core_count * 2) + effective_spindle_count)`
del servidor de Postgres, no arbitrariamente — un valor mayor no es automáticamente mejor.

## Hallazgo: contención de optimistic locking en `registerEntry`

Los 3 scripts registran movimientos de stock contra un único "producto semilla" creado en
`setup()` (para no tener que crear un producto nuevo en cada iteración solo para poder
registrarle una entrada). Con muchos VUs escribiendo la MISMA fila de `Product`
concurrentemente, el `@Version` (optimistic locking, BACK-002) produce `409 Conflict` con el
mensaje `"El producto fue modificado por otro proceso, recargue e intente de nuevo"`.

Verificado aislado (10 VU sin pausa entre iteraciones, peor caso): **83% de las escrituras
chocaban con 409**. Es un comportamiento esperado y correcto del backend ante escrituras
concurrentes sobre el mismo recurso — el propio mensaje de error instruye al cliente a
reintentar. `tests/performance/helpers/retry.js` implementa exactamente ese reintento (hasta
5 intentos, con backoff corto + jitter), que es lo que haría un cliente real — con eso, el
load test a 50 VU llega a **100% de checks exitosos** con solo 0.40% de requests HTTP
individuales en 409 antes del reintento exitoso.

## Soak Test (50 VU, 30 min)

> Corrido el 2026-07-11 en local, en paralelo con la redacción de este documento (proceso en
> segundo plano, sin otra carga simultánea contra el backend).

| Threshold | Resultado | Estado |
|-----------|-----------|--------|
| `p(95) < 500ms` | **53.2ms** | ✅ PASS |
| `http_req_failed < 1%` | **0.47%** | ✅ PASS |
| `http_reqs rate > 100/s` | **95.01 req/s** | ⚠️ FAIL (por poco) |

```
checks_succeeded: 100.00% (169,993 / 169,993)
http_req_duration: avg=27.48ms med=23.49ms p(90)=41.65ms p(95)=53.2ms max=2.56s
http_reqs: 171,112 requests en 30m00.9s (95.01 req/s)
iterations: 169,941 (94.36/s)
vus: 50 (constante)
```

**El único threshold que no se cumple es throughput, y no por degradación.**
`soak-test.js` usa `sleep(0.5)` entre iteraciones (deliberadamente más relajado que
load-test.js — un soak test busca simular carga *sostenida y realista* durante 30 minutos,
no maximizar throughput), lo que limita el ritmo natural de cada VU a ~1.9 iteraciones/s;
con 50 VU eso da ~95 req/s, un poco por debajo del umbral de 100/s calibrado contra el
timing más ajustado de load-test.js. La latencia (p95=53ms, 9× margen) y la tasa de error
(0.47%) — las dos métricas que realmente importan en un soak test — pasan cómodamente.

### Análisis de fuga de memoria (heap JVM, Prometheus `jvm_memory_used_bytes`, ventanas de 1 min)

| Minuto | Heap usado |
|--------|-----------|
| 0 (inicio) | 187 MB |
| 1–10 | 132–251 MB (oscilación, JVM/G1 calentando) |
| 10–20 | 145–195 MB (tendencia suave al alza) |
| 20–30 (fin) | 190–214 MB |

Heap mínimo: 132 MB · máximo puntual: 251 MB · máximo configurado (`-Xmx`): **1986 MB**
(el heap usado nunca superó ~13% de la capacidad configurada).

Hay una tendencia suave al alza a lo largo de los 30 minutos (de un piso de ~150MB a
~200-214MB) — no es tan concluyente como para descartarla con una sola corrida de 30
minutos, pero **la señal más importante para diferenciar "fuga real" de "el heap
encontrando su tamaño de trabajo estable" es la tasa de GC** (`jvm_gc_pause_seconds_count`),
no el heap en sí: 

```
18:37–18:49  0.095 → 0.400 GC/s  (subiendo — calentamiento normal de la JVM)
18:49–19:07  0.400 GC/s CONSTANTE durante los últimos ~20 minutos
```

Una fuga real haría que el GC tuviera que correr con más frecuencia (o pausas más largas)
a medida que queda menos heap disponible para reclamar la misma cantidad de basura — acá
la tasa de GC se estabiliza por completo en 0.400/s y se mantiene exactamente ahí durante
los últimos dos tercios de la corrida. **Conclusión: no hay evidencia de fuga de memoria**
en esta corrida de 30 minutos — el crecimiento de heap observado es consistente con el
comportamiento normal de ajuste adaptativo de G1GC bajo carga sostenida, no con una fuga
progresiva. Para una confirmación más fuerte (recomendado antes de un despliegue real a
producción) valdría correr el soak test durante 2-4 horas y confirmar que la tasa de GC
sigue plana — no se hizo en esta sesión por el costo de tiempo.

### Cómo monitorear un soak test en vivo

Mientras corre, abrir el dashboard **"Inventario Backend"** en Grafana
(`http://localhost:3000/d/inventario-backend`, OBS-004) y observar en particular:

- **JVM Heap Used**: una fuga de memoria se ve como una tendencia de crecimiento sostenido
  que el GC no logra bajar de vuelta a un piso estable entre picos (a diferencia del
  diente de sierra saludable observado en load-test/stress-test, ver arriba).
- **HikariCP Connections**: un pool que se agota progresivamente (conexiones activas que
  suben y nunca vuelven a bajar, o conexiones "pendientes" creciendo) indica una fuga de
  conexiones (ej. un `try-with-resources` faltante), no solo saturación transitoria bajo
  pico como en el stress test.

## Integración en CI

`.github/workflows/performance-test.yml` corre **solo `load-test.js`** (50 VU / 5 min) en
cada push a `develop` y PR a `develop`/`main` — ni `stress-test.js` (~8 min, satura el pool
a propósito) ni `soak-test.js` (30 min, ticket lo especifica "en local") son apropiados para
correr en cada PR. Igual que TEST-004/TEST-005, corre contra `docker-compose.dev.yml`
(Postgres + Keycloak + backend) por falta de `docker-compose.staging.yml` (INFRA-004).
Sube `tests/performance/results/load-test-summary.json` y `load-test-results.json` (formato
JSON completo de series de tiempo, `--out json=`) como artifact.

## Nota sobre los datos generados

Los 3 scripts crean productos con `category: "Performance"` (createProduct corre
continuamente durante toda la duración del script) — a diferencia de los volúmenes pequeños
de datos de prueba que dejan los E2E/API tests de tickets anteriores, las corridas
completas de esta sesión (load + stress + soak) generaron **60,269 productos**. Por ese
volumen, sí se limpiaron al terminar (a diferencia del criterio de "dejar los datos" ya
establecido para E2E/API testing, donde el volumen es de un puñado de filas por corrida):
`UPDATE products SET status = 'INACTIVE' WHERE category = 'Performance' AND status =
'ACTIVE';` directo por SQL (equivalente exacto al soft-delete de `DELETE
/api/products/{id}`, ADR-001, pero en un solo statement en vez de 60 mil llamadas HTTP
individuales) — quedan fuera del listado activo por defecto sin necesitar limpiar la tabla
físicamente. El producto semilla de cada script (`PERF-*-SEED-*`) recibe `quantity:
1000000`+ para no quedarse sin stock durante la corrida.
