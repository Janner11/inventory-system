# Observabilidad

Resumen del stack de observabilidad del proyecto y dónde encontrar cada
pieza. Para el detalle de decisiones y verificaciones de cada ticket, ver
`CLAUDE.md` (local, no versionado) secciones "Detalle de OBS-001/002/003/004".

## Componentes

| Componente | Rol | Config | URL local |
|---|---|---|---|
| **Grafana Alloy** | Colector OTLP central (gRPC `4317` / HTTP `4318`); enruta métricas → Prometheus, trazas → Tempo, logs → Loki | `observability/alloy/alloy-config.alloy` | http://localhost:12345 |
| **Prometheus** | Métricas del backend (`/actuator/prometheus`, Micrometer), de Alloy (self-monitoring, OBS-002) y de cAdvisor (por contenedor, OBS-004) | `observability/prometheus/prometheus.yml` | http://localhost:9090 |
| **cAdvisor** | Métricas de CPU/RAM/red/disco por contenedor (dashboard "Infraestructura", OBS-004) | `docker-compose.dev.yml` (sin config propia) | http://localhost:8085 |
| **Loki** | Logs estructurados del backend, exportados por OTLP (OBS-001) | `observability/loki/loki-config.yml` | http://localhost:3100 |
| **Tempo** | Trazas distribuidas del backend, exportadas por OTLP (OBS-001) | `observability/tempo/tempo-config.yml` | http://localhost:3200 |
| **Grafana** | 4 dashboards (Aplicación/Infraestructura/Negocio/Seguridad, OBS-004) + Explore, datasources provisionados como código | `observability/grafana/provisioning/` | http://localhost:3000 (`admin`/`admin`) |
| **Alertmanager** | Enrutamiento de las 5 alertas de Prometheus (CPU, error rate, latencia, servicio caído, fallos de auth — OBS-005) a un webhook local | `observability/alertmanager/alertmanager.yml` | http://localhost:9093 |

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
- **"¿Cuánto tarda un endpoint?"** → Grafana → dashboard "Aplicación"
  (OBS-004) o Prometheus directamente (`http_server_requests_seconds`).
- **"¿El contenedor X está usando mucha CPU/memoria?"** → Grafana →
  dashboard "Infraestructura" (OBS-004, cAdvisor).
- **"¿Cuántos productos/movimientos hay, o cuánto vale el inventario?"** →
  Grafana → dashboard "Negocio" (OBS-004).
- **"¿Hay intentos de acceso no autorizados?"** → Grafana → dashboard
  "Seguridad" (OBS-004).
- **"¿Por qué falló este request?"** → Grafana → Explore → Loki, ver
  [`loki-queries.md`](./loki-queries.md) para las consultas de uso frecuente
  (por `traceId`, por `severity`, por clase Java).
- **"¿Qué pasó en toda la cadena de este request?"** → copiar el `traceId`
  del log (o del botón "TraceID" del datasource de Loki) y buscarlo en
  Grafana → Explore → Tempo.

## Documentos relacionados

- [`loki-queries.md`](./loki-queries.md) — consultas LogQL de referencia,
  esquema de los logs JSON y configuración de retención/límites (OBS-003).
- [`alerts.md`](./alerts.md) — las 5 reglas de alerta de Prometheus, sus
  umbrales y motivación, y la verificación real de disparo/entrega (OBS-005).
