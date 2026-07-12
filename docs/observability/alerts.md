# Alertas de Prometheus (OBS-005)

Las 5 alertas requeridas por el ticket, definidas en
[`observability/prometheus/rules/alerts.yml`](../../observability/prometheus/rules/alerts.yml)
y enrutadas por [`observability/alertmanager/alertmanager.yml`](../../observability/alertmanager/alertmanager.yml).
Para el detalle completo de la implementación (bugs encontrados, decisiones,
verificación paso a paso), ver `CLAUDE.md` (local, no versionado), sección
"Detalle de OBS-005".

## Las 5 alertas

| Alerta | Expresión (resumen) | Umbral | Duración (`for`) | Severidad |
|---|---|---|---|---|
| **HighCPU** | `system_cpu_usage * 100` | > 80% | 5m | warning |
| **HighErrorRate** | proporción de respuestas 5xx sobre el total | > 5% | 2m | critical |
| **HighLatency** | `histogram_quantile(0.95, ...) * 1000` | > 1000ms | 3m | warning |
| **ServiceDown** | `up` | == 0 | 1m | critical |
| **AuthFailures** | `increase(...401\|403...[1m])` | > 10 | 1m | warning |

## Motivación de cada umbral y métrica elegida

### HighCPU

```promql
system_cpu_usage{job="inventario-backend"} * 100 > 80
```

Se usa `system_cpu_usage` (gauge de Micrometer, uso de CPU de **todo el
sistema operativo** del contenedor, rango 0.0–1.0) en vez de
`process_cpu_usage` (que solo mide el proceso JVM) porque el nombre del
ticket ("cpu_usage") describe el uso general del host/contenedor, no
solo el del proceso Java. `for: 5m` evita alertar por picos cortos de CPU
(ej. un GC puntual o el arranque de un test) — solo un sostenido de 5
minutos indica un problema real de capacidad.

### HighErrorRate

```promql
(
  sum(rate(http_server_requests_seconds_count{job="inventario-backend", status=~"5.."}[2m]))
  /
  sum(rate(http_server_requests_seconds_count{job="inventario-backend"}[2m]))
) * 100 > 5
```

Proporción de respuestas `5xx` sobre el total de requests en una ventana de
2 minutos. Si no hay tráfico en la ventana, `rate()` de ambas partes es 0 y
la división da `NaN` — una comparación `NaN > 5` es siempre falsa en
PromQL, así que un backend simplemente inactivo (sin tráfico) no dispara un
falso positivo. `for: 2m` es el valor exacto pedido por el ticket: da
margen para que un error aislado (ej. un timeout puntual de red) no
dispare la alerta, pero sigue siendo lo bastante corto para detectar un
despliegue roto rápido.

### HighLatency

```promql
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{job="inventario-backend"}[3m])) by (le)
) * 1000 > 1000
```

`http_server_requests_seconds_bucket` es el histograma que Micrometer ya
expone (los mismos buckets que usa el panel "HTTP Latency p95" del
dashboard "Aplicación" de OBS-004) — no hizo falta ninguna métrica nueva.
Los buckets están en segundos; se multiplica por 1000 dentro del propio
`expr` (no en la anotación) para que tanto el umbral (`1000`) como el
`$value` mostrado en la notificación queden en milisegundos — Prometheus no
tiene una función de template equivalente a `mulByScalar` de Grafana/Loki,
así que la conversión se hace en la propia consulta. `for: 3m`, tal como
pide el ticket.

### ServiceDown

```promql
up == 0
```

Sin filtro de `job` a propósito: cubre los 4 scrape jobs actuales
(`prometheus`, `inventario-backend`, `alloy`, `cadvisor`, ver
`observability/prometheus/prometheus.yml`) y cualquiera que se agregue en
el futuro sin tener que tocar esta regla. `for: 1m` es deliberadamente
corto — un servicio caído es siempre urgente, a diferencia de un pico de
CPU o de errores que puede ser transitorio.

### AuthFailures

```promql
sum(increase(http_server_requests_seconds_count{job="inventario-backend", status=~"401|403", uri=~"/api/.*"}[1m])) > 10
```

**Adaptación explícita:** el ticket especifica `auth_failures_total > 10 por
minuto`, pero el backend no expone ningún contador dedicado con ese nombre
— no se agregó instrumentación nueva para esta alerta. En su lugar se
reutiliza el conteo de respuestas `401` (token ausente/inválido) y `403`
(scope insuficiente) sobre `/api/**`, la misma métrica que ya usa el panel
"Intentos fallidos (401+403)" del dashboard "Seguridad" de OBS-004 — mismo
criterio ya aplicado en ese ticket ("reutilizar métricas existentes en vez
de agregar instrumentación cuando una métrica ya existente captura la misma
señal", ver `CLAUDE.md`). `increase(...[1m])` en vez de `rate(...) * 60`
porque para un conteo absoluto ("más de 10 en el último minuto") es más
directo y menos propenso a redondeos.

## Receiver de Alertmanager

`route.receiver: default` apunta a un `webhook_configs` real:
`http://alert-webhook-receiver:5001/alert` — un servicio nuevo y mínimo
(`observability/alertmanager/webhook-receiver.py`, agregado a
`docker-compose.dev.yml`) que registra en stdout cada alerta que recibe.
No existe ningún servicio real de Slack/email en este entorno de
desarrollo; este webhook local, offline y committeado permite verificar
"Alertmanager recibe alertas" contra un target real en vez de un
placeholder sin probar. Para producción, `receivers` se reemplazaría por
`slack_configs`/`email_configs` reales apuntando a canales/buzones de
verdad.

## Cómo reproducir la verificación

```bash
# 1. Confirmar que las 5 reglas cargaron sin error
curl -s http://localhost:9090/api/v1/rules | jq '.data.groups[].rules[] | {name, state, health}'

# 2. Ver alertas activas en Prometheus
open http://localhost:9090/alerts

# 3. Ver alertas recibidas por Alertmanager
curl -s http://localhost:9093/api/v2/alerts | jq
open http://localhost:9093

# 4. Confirmar que el webhook local las recibió
docker logs inventario-alert-webhook-receiver --tail 50
```

Para simular `HighErrorRate`: generar tráfico sostenido contra un endpoint
que produce un 500 real, por ejemplo `GET /api/products?sort=noexiste,asc`
(Spring Data no puede resolver una propiedad de ordenamiento inexistente y
lanza una excepción no capturada por `GlobalExceptionHandler`, resultando
en `500`).

Para simular `HighLatency`: generar carga concurrente sostenida contra un
endpoint más pesado (por ejemplo `GET /api/reports/inventory`, que agrega
las 4 consultas del dashboard) durante más de 3 minutos.

`ServiceDown` y `AuthFailures` no necesitan ningún tráfico especial para
verificarse: basta con detener cualquier contenedor scrapeado (`docker stop
inventario-backend`, o cualquier otro con un job en `prometheus.yml`) para
`ServiceDown`, o generar más de 10 respuestas 401/403 en un minuto (por
ejemplo, dejando expirar el access token de Keycloak — 5 minutos por
defecto — y siguiendo llamando a un endpoint protegido) para `AuthFailures`.

## Verificación real (2026-07-12)

Las 5 alertas se dispararon de verdad contra el stack real
(`docker-compose.dev.yml`) y se confirmó la entrega completa
Prometheus → Alertmanager → webhook para cada una. `ServiceDown` se
disparó orgánicamente (el contenedor `cadvisor` de OBS-004 estaba caído en
el momento de esta verificación — evidencia real, no simulada). Las otras
4 se generaron con tráfico real: `GET /api/products?sort=noexiste,asc`
(Spring Data no puede resolver esa propiedad de ordenamiento y lanza una
`PropertyReferenceException` no capturada → `500` real) sostenido con
varios workers concurrentes para `HighErrorRate`, y carga concurrente
pesada (cientos de requests simultáneos contra `/api/reports/inventory` y
endpoints similares) para `HighLatency`/`HighCPU`; `AuthFailures` se
disparó al dejar expirar el access token de Keycloak mientras el tráfico
seguía llamando a un endpoint protegido.

Historial real capturado por `docker logs inventario-alert-webhook-receiver`
(recorte, hora UTC):

```
[2026-07-12T03:15:49Z] ServiceDown   [firing/critical]
[2026-07-12T03:42:19Z] HighErrorRate [firing/critical]
[2026-07-12T03:45:04Z] AuthFailures  [firing/warning]
[2026-07-12T03:47:19Z] HighErrorRate [resolved/critical]
[2026-07-12T03:50:04Z] AuthFailures  [resolved/warning]
[2026-07-12T05:40:49Z] HighLatency   [firing/warning]
[2026-07-12T05:45:49Z] HighLatency   [resolved/warning]
[2026-07-12T13:07:02Z] HighErrorRate [firing/critical]
[2026-07-12T13:08:02Z] HighLatency   [firing/warning]
[2026-07-12T13:17:32Z] HighCPU       [firing/warning]
```

Las 5 alertas completaron el ciclo `pending → firing` respetando su
`for` configurado (verificado con `activeAt` en `/api/v1/rules` — por
ejemplo, `HighCPU` quedó `pending` a las `13:06:18Z` con CPU sostenida
>90% y pasó a `firing` exactamente a los 5 minutos, a las `13:11:xx`–`13:17Z`
según la ventana de evaluación), y las reglas mantuvieron `health: ok`
durante toda la verificación (sin errores de sintaxis/evaluación de
PromQL). El receptor webhook (`inventario-alert-webhook-receiver`)
recibió y registró cada notificación con su `alertname`/`status`/`severity`,
confirmando "Alertmanager recibe alertas" contra un target real.
