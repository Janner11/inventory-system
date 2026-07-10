# Consultas LogQL — Grafana Loki (OBS-003)

> Todas las consultas de este documento fueron probadas contra la instancia
> real de Loki del entorno de desarrollo (`docker compose -f docker-compose.dev.yml up -d`,
> `http://localhost:3100`), ya sea vía `curl` a la API HTTP de Loki o desde
> Grafana → Explore → datasource `Loki`.

## Contexto

Desde OBS-001, el backend (`inventario-backend`) exporta sus logs por OTLP al
colector de Grafana Alloy (`observability/alloy/alloy-config.alloy`), que los
reenvía a Loki (`otelcol.exporter.loki`). Cada línea de log llega a Loki como
un **objeto JSON estructurado** (no texto plano de consola), con esta forma:

```json
{
  "body": "Producto creado: id=eae1597b-..., sku=OBS-003-VERIFY",
  "traceid": "3ba9150a872b7e02e1e6515282b114ae",
  "spanid": "d69fdbb9cc56329e",
  "severity": "INFO",
  "flags": 1,
  "resources": {
    "service.name": "inventario-backend",
    "service.version": "0.1.0-SNAPSHOT",
    "container.id": "...",
    "host.name": "...",
    "telemetry.distro.name": "opentelemetry-java-instrumentation",
    "...": "..."
  },
  "instrumentation_scope": { "name": "com.inventario.service.ProductService" }
}
```

**Labels indexados** (los que Loki usa para seleccionar streams, ver `{...}`
al inicio de cada query): `job`, `instance`, `exporter`, `level`. El resto de
los campos (`traceid`, `spanid`, `severity`, `resources.*`,
`instrumentation_scope.name`) están **dentro del body JSON** — para
filtrar/agrupar por ellos hace falta el parser `| json` de LogQL, que los
expone aplanados (ej. `resources.service.name` → `resources_service_name`).

## Consultas de uso frecuente

### 1. Todos los logs del backend

```logql
{job="inventario-backend"}
```

Punto de partida en Grafana Explore. Equivale a `docker logs inventario-backend`
pero indexado y con retención configurada (ver más abajo).

### 2. Solo logs de error

```logql
{job="inventario-backend"} | json | severity="ERROR"
```

### 3. Correlación log → traza (buscar un `traceId` específico)

```logql
{job="inventario-backend"} |= "<traceId>"
```

Reemplazar `<traceId>` por el valor visto en `docker logs inventario-backend`
(`[traceId=...] [spanId=...]`, `logback-spring.xml`, OBS-001) o copiado desde
un span en Tempo. Este es el mismo patrón que usa el botón "TraceID"
(derived field) del datasource de Loki en Grafana para saltar de un log a su
traza en Tempo — ver `observability/grafana/provisioning/datasources/loki.yml`.

### 4. Logs de una clase/servicio específico del backend

```logql
{job="inventario-backend"} | json | instrumentation_scope_name=~"com.inventario.service.*"
```

Útil para aislar los logs de `ProductService`, `StockService`, etc. — el
nombre del logger SLF4J queda en `instrumentation_scope.name`.

### 5. Tasa de errores en los últimos 5 minutos (para un futuro panel/alerta)

```logql
sum(count_over_time({job="inventario-backend"} | json | severity="ERROR" [5m]))
```

Base para una regla de Alertmanager (OBS-005, pendiente) o un panel del
dashboard "Aplicación" (OBS-004) — hoy ese dashboard solo usa métricas de
Prometheus/Micrometer, no logs.

### 6. Buscar por texto libre en el mensaje

```logql
{job="inventario-backend"} |= "Stock insuficiente"
```

`|=` filtra por substring sin necesidad de parsear el JSON — más rápido que
`| json` cuando alcanza con buscar texto plano dentro de `body`.

## Retención y límites (OBS-003)

`observability/loki/loki-config.yml` configura:

- `compactor.retention_enabled: true` + `limits_config.retention_period: 168h`
  (7 días) — los logs se borran automáticamente después de una semana.
  Suficiente para depurar y correlacionar trazas recientes en un entorno de
  curso/desarrollo; no está pensado como almacenamiento a largo plazo.
- `limits_config.ingestion_rate_mb`/`ingestion_burst_size_mb`/
  `per_stream_rate_limit` — evitan que un bug de logging en bucle (o un
  volumen de tráfico inesperado) llene el disco del contenedor sin ninguna
  señal previa; Loki empieza a devolver `429` en la ingesta si se exceden.

Verificar la configuración cargada en un Loki corriendo:

```bash
curl -s http://localhost:3100/config | grep -A2 retention_period
```
