# Observabilidad

Resumen del stack de observabilidad del proyecto y dónde encontrar cada
pieza. Para el detalle de decisiones y verificaciones de cada ticket, ver
`CLAUDE.md` (local, no versionado) secciones "Detalle de OBS-001/002/003/004".

## Componentes

| Componente | Rol | Config | URL local |
|---|---|---|---|
| **Grafana Alloy** | Colector OTLP central (gRPC `4317` / HTTP `4318`); enruta métricas → Prometheus, trazas → Tempo, logs → Loki | `observability/alloy/alloy-config.alloy` | http://localhost:12345 |
| **Prometheus** | Métricas del backend (`/actuator/prometheus`, Micrometer) y del propio Alloy (self-monitoring, OBS-002) | `observability/prometheus/prometheus.yml` | http://localhost:9090 |
| **Loki** | Logs estructurados del backend, exportados por OTLP (OBS-001) | `observability/loki/loki-config.yml` | http://localhost:3100 |
| **Tempo** | Trazas distribuidas del backend, exportadas por OTLP (OBS-001) | `observability/tempo/tempo-config.yml` | http://localhost:3200 |
| **Grafana** | Dashboards + Explore, datasources de los 3 anteriores provisionados como código | `observability/grafana/provisioning/` | http://localhost:3000 (`admin`/`admin`) |
| **Alertmanager** | Enrutamiento de alertas de Prometheus (reglas: OBS-005, pendiente) | `observability/alertmanager/alertmanager.yml` | http://localhost:9093 |

## Flujo de datos

```
Backend (OTel Java Agent, OBS-001)
    └─► OTLP gRPC :4317 → Grafana Alloy
              ├─► Prometheus (remote_write)  — métricas
              ├─► Tempo (OTLP)               — trazas
              └─► Loki (OTLP)                — logs
                      └─► Grafana (datasources provisionados) → dashboards / Explore
```

El backend también expone métricas Micrometer directamente en
`/actuator/prometheus`, scrapeadas por Prometheus (OBS-004) — dos rutas de
métricas independientes que coexisten sin colisionar (mismo `job`, distinto
`instance`; ver "Detalle de OBS-001" en `CLAUDE.md`).

## Dónde mirar según lo que se busca

- **"¿Está sano el pipeline de observabilidad en sí?"** → Prometheus, job
  `alloy` (self-monitoring, OBS-002): `alloy_component_controller_running_components`,
  `otelcol_exporter_*_failed`.
- **"¿Cuánto tarda un endpoint?"** → Grafana → dashboard "Inventario Backend"
  (OBS-004) o Prometheus directamente (`http_server_requests_seconds`).
- **"¿Por qué falló este request?"** → Grafana → Explore → Loki, ver
  [`loki-queries.md`](./loki-queries.md) para las consultas de uso frecuente
  (por `traceId`, por `severity`, por clase Java).
- **"¿Qué pasó en toda la cadena de este request?"** → copiar el `traceId`
  del log (o del botón "TraceID" del datasource de Loki) y buscarlo en
  Grafana → Explore → Tempo.

## Documentos relacionados

- [`loki-queries.md`](./loki-queries.md) — consultas LogQL de referencia,
  esquema de los logs JSON y configuración de retención/límites (OBS-003).
