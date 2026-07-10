# Sistema de Gestión de Inventarios Empresarial

Proyecto académico (PUCMM — Aseguramiento de Calidad de Software) para la gestión de
inventarios de pequeñas empresas. Monorepo compuesto por un frontend en React (Vite) y
un backend en Spring Boot 3 (Java 21), con autenticación vía Keycloak y observabilidad
basada en el stack CNCF: Prometheus (métricas), Loki (logs), Tempo (trazas) y Grafana
Alloy como colector OTLP central, todo visualizado en Grafana.

## Estructura del repositorio

```
frontend/   → SPA en React + Vite
backend/    → API REST en Spring Boot 3 (Java 21)
observability/ → configuración de Prometheus, Alertmanager, Loki, Tempo, Alloy y Grafana
scripts/    → scripts auxiliares (init de base de datos, etc.)
keycloak/   → configuración/exportación del realm
```

## Entorno de desarrollo local (Docker Compose)

El archivo [`docker-compose.dev.yml`](docker-compose.dev.yml) levanta toda la
infraestructura necesaria para el desarrollo local con un solo comando:

- **PostgreSQL 16** — base de datos de la aplicación y de Keycloak
- **Keycloak 24** — Identity & Access Management (IAM)
- **Backend** — API REST Spring Boot (build local desde `backend/Dockerfile`)
- **Prometheus** — recolección de métricas: scrapea al backend (`/actuator/prometheus`),
  al propio Alloy (self-monitoring del pipeline) y a cAdvisor (métricas por contenedor),
  además de exponer el receptor `remote_write` que usa Alloy para reenviar las métricas OTLP
- **cAdvisor** — métricas de CPU/RAM/red por contenedor (dashboard "Infraestructura", OBS-004)
- **Alertmanager** — enrutamiento de alertas de Prometheus (reglas: OBS-005, pendiente)
- **Loki** — almacenamiento de logs estructurados, con retención de 7 días y
  límites de ingesta configurados (OBS-003, ver
  [`docs/observability/loki-queries.md`](docs/observability/loki-queries.md))
- **Tempo** — almacenamiento de trazas distribuidas
- **Grafana Alloy** — colector OTLP central (gRPC 4317 / HTTP 4318) que enruta métricas
  a Prometheus, trazas a Tempo y logs a Loki. El backend ya lo alimenta con datos reales
  vía el OTel Java Agent (OBS-001) — ver "Pipeline de observabilidad (OBS-002)" abajo.
- **Grafana** — dashboards y visualización (datasources de Prometheus/Loki/Tempo
  provisionados automáticamente)

Todos los servicios se conectan a través de la red `inventario-network` y persisten
sus datos en volúmenes de Docker (`postgres_data`, `keycloak_data`, `prometheus_data`,
`grafana_data`, `loki_data`, `tempo_data`, `alertmanager_data`).

> **Nota de versiones:** Loki y Tempo están pineados a `2.9.6` / `2.6.1` (no `:latest`).
> La serie 3.x más reciente de ambos cambia de forma incompatible el esquema de
> configuración usado aquí (Tempo 3.x reescribe el pipeline hacia una arquitectura
> basada en Kafka) y, en el caso de Loki, la imagen `:latest` no trae shell/`wget`,
> lo que impide un `HEALTHCHECK` de Docker. Alertmanager está pineado a `v0.27.0` por
> la misma razón de reproducibilidad.

### Requisitos previos

- Docker Desktop o Docker Engine
- Docker Compose v2 (`docker compose`)

### Pasos para levantar el entorno

1. Copiar el archivo de variables de entorno de ejemplo:

   ```bash
   cp .env.example .env
   ```

2. Levantar todos los servicios:

   ```bash
   docker compose -f docker-compose.dev.yml up -d --build
   ```

3. Verificar que todos los contenedores estén corriendo (y healthy donde aplique):

   ```bash
   docker compose -f docker-compose.dev.yml ps
   ```

4. Acceder a los servicios desde el navegador:

   | Servicio | URL | Credenciales por defecto |
   |----------|-----|---------------------------|
   | Backend (API) | http://localhost:8081/api/ping | — |
   | Backend (Actuator) | http://localhost:8081/actuator/health | — |
   | Keycloak | http://localhost:8080 | `admin` / `admin` (consola admin) |
   | Prometheus | http://localhost:9090 | — |
   | cAdvisor | http://localhost:8085 | — |
   | Alertmanager | http://localhost:9093 | — |
   | Loki | http://localhost:3100/ready | — (se consulta desde Grafana Explore) |
   | Tempo | http://localhost:3200/status | — (se consulta desde Grafana Explore) |
   | Grafana Alloy (UI) | http://localhost:12345 | — |
   | Grafana | http://localhost:3000 | `admin` / `admin` |

### Keycloak — realm `inventario` (SEC-001)

El realm `inventario` se importa automáticamente al levantar Keycloak desde
[`keycloak/realm.json`](keycloak/realm.json) (flag `--import-realm`). Incluye:

- **Clients:** `inventario-frontend` (público, PKCE, redirect `http://localhost:5173/*`)
  y `inventario-backend` (confidencial, secret de dev `inventario-backend-secret`,
  `directAccessGrantsEnabled=true` para pruebas con `curl`).
- **Scopes (client roles en `inventario-backend`):** `product:view`, `product:manage`,
  `stock:view`, `stock:manage`, `report:view`, `user:manage`, `audit:view`.
- **Roles de realm (composite, combinan los scopes anteriores):** `ADMIN`, `MANAGER`,
  `WAREHOUSE`, `VIEWER`, `AUDITOR` — ver matriz de permisos completa en `CLAUDE.md`
  (sección 6). Keycloak expande los roles composite al generar el JWT, por lo que
  `resource_access.inventario-backend.roles` siempre contiene los scopes resueltos
  (verificado con `curl` para los 5 usuarios de prueba).
- **Usuarios de prueba:**

  | Usuario | Password | Rol de realm | Scopes resueltos en el JWT |
  |---------|----------|---------------|------------------------------|
  | `admin@test.com` | `admin123` | `ADMIN` | los 7 scopes |
  | `manager@test.com` | `manager123` | `MANAGER` | `product:view`, `product:manage`, `stock:view`, `stock:manage`, `report:view` |
  | `warehouse@test.com` | `warehouse123` | `WAREHOUSE` | `product:view`, `stock:view`, `stock:manage` |
  | `viewer@test.com` | `viewer123` | `VIEWER` | `product:view`, `stock:view`, `report:view` |
  | `auditor@test.com` | `auditor123` | `AUDITOR` | `audit:view`, `report:view` |

> Nota: la importación de realm con `IGNORE_EXISTING` solo aplica una vez por
> volumen. Si se modifica `keycloak/realm.json` y se quiere reimportar, hay que
> recrear el volumen `keycloak_data` (`docker compose -f docker-compose.dev.yml down -v`
> y volver a levantar).

#### Obtener un token JWT (dev, sin frontend)

```bash
curl -s -X POST "http://localhost:8080/realms/inventario/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=inventario-backend" \
  -d "client_secret=inventario-backend-secret" \
  -d "username=viewer@test.com" \
  -d "password=viewer123"
```

#### Endpoints protegidos de prueba (SEC-002)

```bash
# Sin token → 401
curl -i http://localhost:8081/api/ping/secure

# Con token de admin@test.com o viewer@test.com (requiere scope product:view) → 200
curl -i -H "Authorization: Bearer <ACCESS_TOKEN>" http://localhost:8081/api/ping/secure

# Con token de admin@test.com (requiere scope product:manage) → 200
curl -i -X POST -H "Authorization: Bearer <ACCESS_TOKEN_ADMIN>" http://localhost:8081/api/ping/manage

# Con token de viewer@test.com (sin scope product:manage) → 403
curl -i -X POST -H "Authorization: Bearer <ACCESS_TOKEN_VIEWER>" http://localhost:8081/api/ping/manage
```

### Auditoría — Hibernate Envers (BACK-003)

La entidad `Product` está anotada con `@Audited`. Cada `INSERT`/`UPDATE`/`DELETE`
genera una fila en `products_aud` (snapshot del producto en esa revisión) y una
fila en `revinfo` (timestamp de la revisión). La migración
[`V3__create_audit_tables.sql`](backend/src/main/resources/db/migration/V3__create_audit_tables.sql)
crea ambas tablas.

Para ver el historial de revisiones de un producto desde `psql`:

```sql
SELECT p.rev, r.revtstmp, p.sku, p.name, p.quantity, p.status,
       CASE p.revtype WHEN 0 THEN 'ADD' WHEN 1 THEN 'MOD' WHEN 2 THEN 'DEL' END AS revision_type
FROM products_aud p
JOIN revinfo r ON r.rev = p.rev
WHERE p.id = '<PRODUCT_ID>'
ORDER BY p.rev;
```

**Endpoint de auditoría (BACK-007)**: `GET /api/audit/products/{id}/revisions`
(scope `product:view`) expone el mismo historial vía API/Swagger, sin
necesidad de `psql`. Devuelve un array con un snapshot del producto por cada
revisión (`revisionNumber`, `revisionTimestamp`, `revisionType` ADD/MOD/DEL y
los campos del producto en esa revisión). Responde 404 si el producto no
tiene historial de auditoría.

```bash
curl -H "Authorization: Bearer <ACCESS_TOKEN>" \
  http://localhost:8081/api/audit/products/<PRODUCT_ID>/revisions
```

5. Para detener y eliminar los contenedores (los datos persisten en los volúmenes):

   ```bash
   docker compose -f docker-compose.dev.yml down
   ```

   Para eliminar también los volúmenes (reinicio completo de datos):

   ```bash
   docker compose -f docker-compose.dev.yml down -v
   ```

### API Testing — RestAssured (TEST-003)

`backend/src/test/java/com/inventario/api/ProductApiTest.java` contiene
**13 escenarios de API testing** con RestAssured contra un servidor HTTP real
(`@SpringBootTest(webEnvironment = RANDOM_PORT)`) y una base de datos PostgreSQL
levantada vía Testcontainers. No requiere Keycloak — el `JwtDecoder` se
reemplaza con un mock que emite tokens controlados de admin
(`product:view + product:manage`) y viewer (`product:view`).

| Tipo | Escenarios |
|---|---|
| Validación de permisos 401 | Sin token: `GET /products`, `POST /products`, `POST /stock/entry` |
| Validación de permisos 403 | Scope insuficiente: `POST /products` (viewer), `PUT /products/{id}` (viewer), `POST /stock/entry` (viewer) |
| Validación de errores | `POST /products` con body inválido → 400; `GET /products/{id}` inexistente → 404 |
| Rutas exitosas | `GET /products` → 200; `POST /products` → 201; `GET /products/{id}` → 200; `PUT /products/{id}` → 200; `DELETE /products/{id}` → 204 |

```bash
cd backend && ./gradlew test --tests "com.inventario.api.ProductApiTest"
```

### E2E Testing — Playwright (TEST-004)

`frontend/tests/e2e/` contiene **7 escenarios E2E** con Playwright (Chromium) que verifican el flujo completo del usuario contra el stack real (frontend Vite + backend Spring Boot + Keycloak + PostgreSQL vía Docker).

| Archivo | Escenarios |
|---|---|
| `auth.spec.js` | Login via Keycloak redirige a `/dashboard`; navbar/sidebar visibles post-login; logout regresa a `/` |
| `products.spec.js` | Crear producto → aparece en lista; Editar producto → datos actualizados; Eliminar producto → desaparece de lista; Flujo CRUD completo (crear → leer → editar → eliminar) |

**Requisitos previos para ejecutar:**

```bash
# 1. Levantar el stack completo
docker compose -f docker-compose.dev.yml up -d

# 2. Iniciar el frontend (en otra terminal)
cd frontend && npm run dev

# 3. (Primera vez) Instalar browsers de Playwright
cd frontend && npx playwright install chromium
```

**Ejecutar los tests:**

```bash
cd frontend && npm run test:e2e
```

### Control de Stock (BACK-005)

Cada entrada, salida o ajuste de stock de un producto genera un registro en
`stock_movements` (migración
[`V4__create_stock_movements_table.sql`](backend/src/main/resources/db/migration/V4__create_stock_movements_table.sql)),
con FK a `products`. `StockService` expone tres operaciones:

- **Entrada** (`type=ENTRY`): incrementa `quantity` del producto.
- **Salida** (`type=EXIT`): decrementa `quantity`; si la cantidad solicitada
  supera el stock disponible, lanza `InsufficientStockException` (422).
- **Ajuste** (`type=ADJUSTMENT`): fija `quantity` a un valor absoluto (p. ej.
  tras un conteo físico).

En los tres casos se valida que el producto exista (404 si no) y esté
`ACTIVE` (409 `ProductInactiveException` si está `INACTIVE`). Cada
`StockMovement` guarda `previousQuantity`, `newQuantity` y `quantity` (delta)
para mantener trazabilidad completa.

**Alerta de stock bajo**: después de cada movimiento, si
`product.quantity < product.minStock`, se emite un `log.warn(...)` con el SKU,
nombre y cantidades del producto. Esta alerta queda disponible para que
herramientas de observabilidad (p. ej. Loki/Grafana, BACK-008) la consuman más
adelante.

> Los endpoints REST `/api/stock/*` (controller) y los scopes `stock:view`/
> `stock:manage` quedan fuera de este alcance — se implementarán en BACK-006 y
> en la ampliación de seguridad correspondiente.

### Frontend (React + Vite)

Con el backend, Postgres y Keycloak corriendo vía
`docker compose -f docker-compose.dev.yml up -d`, el frontend se ejecuta por
fuera de Docker en modo desarrollo:

```bash
cd frontend
cp .env.example .env   # ajustar solo si las URLs por defecto no aplican
npm install
npm run dev
```

La SPA queda disponible en `http://localhost:5173/`. Desde ahí:

- `/` (`HomePage`): página pública con botón "Iniciar sesión" (redirige a
  Keycloak vía PKCE — SEC-003). Si ya hay sesión, redirige a `/dashboard`.
- `/dashboard` y `/products`: rutas protegidas (`ProtectedRoute`), envueltas
  en el layout principal (`AppShell`, FRONT-001):
  - **Navbar** (arriba): nombre de la app, usuario autenticado
    (`preferred_username`) y botón "Cerrar sesión".
  - **Sidebar** (izquierda): enlaces a "Dashboard" y "Productos", con el
    enlace activo resaltado (`NavLink`/`aria-current="page"`).
  - `DashboardPage`: smoke-test de `GET /api/products` (SEC-003).
  - `ProductsPage` (FRONT-003): lista de productos (`GET /api/products`)
    con búsqueda por nombre/SKU, filtro por categoría, filtro "Solo stock
    bajo" (`quantity < minStock`) y paginación — todo del lado del cliente
    (ver nota más abajo). El formulario de creación/edición se implementa en
    FRONT-004.
- Cualquier otra ruta muestra `NotFoundPage` (404).

### Módulo de Productos (FRONT-003 — Lista, Búsqueda, Filtros y Paginación)

`ProductsPage` consume `GET /api/products` (vía `useProducts`, React Query)
y aplica búsqueda, filtros y paginación **del lado del cliente**:

- **Búsqueda**: por nombre o SKU (case-insensitive, coincidencia parcial).
- **Filtros**: por categoría (`<select>` con las categorías presentes en los
  productos) y "Solo stock bajo" (`quantity < minStock`, mismo criterio de
  alerta de BACK-005).
- **Paginación**: 5 productos por página, con controles "Anterior"/"Siguiente".

> `GET /api/products` (BACK-003, avance — scope reducido) devuelve la lista
> completa de productos `ACTIVE` sin paginación, orden ni filtros en el
> servidor. Dado que el dataset de este avance es pequeño, búsqueda, filtros
> y paginación se implementaron en el frontend sobre esa lista completa, sin
> requerir cambios en el backend. Si el dataset creciera, estos mismos
> controles deberían migrar a parámetros de query (`?q=&category=&page=&size=`)
> resueltos por el backend — ver "Próximos pasos sugeridos".

### Observabilidad (OBS-004 — 4 dashboards de Grafana)

Con el stack levantado (`docker compose -f docker-compose.dev.yml up -d`),
Grafana (`http://localhost:3000`, `admin`/`admin`) trae **provisionados
automáticamente** los 4 dashboards que define la sección 10 del backlog
(`observability/grafana/provisioning/dashboards/`), todos con `refresh: 10s`
y sin configuración manual (provider `inventario`, `dashboards.yml`):

- **Aplicación** (`inventario-backend`, "Inventario Backend"): el backend
  expone métricas en `/actuator/prometheus` (Micrometer), scrapeadas cada
  15s (job `inventario-backend`). Paneles: **Backend Up**, **HTTP Request
  Rate**, **HTTP Error Rate**, **HTTP Latency p95** (requiere
  `management.metrics.distribution.percentiles-histogram.http.server.requests=true`,
  ya en `application.yml`), **JVM Heap Used**, **JVM Live Threads**,
  **HikariCP Connections**.
- **Infraestructura** (`inventario-infra`): métricas de CPU/RAM/red/disco
  **por contenedor**, vía [cAdvisor](https://github.com/google/cadvisor)
  (`gcr.io/cadvisor/cadvisor:v0.47.2`, job `cadvisor` en Prometheus).
  Paneles: **Contenedores activos**, **CPU por contenedor**, **Memoria por
  contenedor**, **Red recibida/enviada por contenedor**, **Disco usado por
  contenedor** — uno por cada uno de los 10 servicios del stack.
- **Negocio** (`inventario-business`): métricas custom de Micrometer
  expuestas por `config/BusinessMetricsConfig.java` junto con las técnicas
  en `/actuator/prometheus` (sin endpoint nuevo): `products` (por status),
  `products_critical` (bajo stock mínimo), `inventory_value` (precio ×
  cantidad del inventario activo) y `stock_movements` (histórico, por
  tipo ENTRY/EXIT/ADJUSTMENT). Paneles: **Productos activos**, **Productos
  en alerta**, **Valor total del inventario**, **Movimientos de stock por
  tipo**, **Tasa de movimientos por minuto**, **Productos por status**.
- **Seguridad** (`inventario-security`): derivada de
  `http_server_requests_seconds_count{status=~"401|403"}` (ya expuesta por
  Micrometer, sin instrumentación nueva). Paneles: **Intentos fallidos
  (401+403) última hora**, **Tasa de 401**, **Tasa de 403**, tabla de
  **endpoints con más 401/403**.

> Las 4 métricas de negocio son *gauges* recalculados contra la base de
> datos en cada scrape de Prometheus (no *counters* incrementados en el
> momento del evento) — Micrometer descarta automáticamente cualquier
> sufijo `_total` en gauges (esa convención de Prometheus se reserva para
> counters), por eso los nombres finales son `products`/`products_critical`/
> `inventory_value`/`stock_movements` sin sufijo.

### Trazas distribuidas y logs correlacionados (OBS-001)

El backend corre instrumentado automáticamente por el
[OpenTelemetry Java Agent](https://github.com/open-telemetry/opentelemetry-java-instrumentation)
(`-javaagent:/app/otel-javaagent.jar`, inyectado vía `JAVA_TOOL_OPTIONS` en el
`Dockerfile`, sin cambios en el código de producción — ADR-006). Exporta
trazas, métricas y logs por OTLP/gRPC a Grafana Alloy (`http://alloy:4317`,
listo desde INFRA-003), que las enruta a Tempo, Prometheus y Loki
respectivamente.

- **Trazas**: cada request HTTP genera un trace visible en Tempo
  (`http://localhost:3200`, o desde Grafana → Explore → datasource `Tempo`).
- **Logs correlacionados**: la consola del backend (`docker logs inventario-backend`)
  imprime `[traceId=...] [spanId=...]` en cada línea de log emitida dentro de
  un request (vacío fuera de un span activo, ej. en el arranque) —
  `logback-spring.xml` lee esos valores del MDC que el agente puebla
  automáticamente. Desde Grafana → Explore → datasource `Loki`, cada log
  tiene un botón "TraceID" (derived field) que salta directo a su trace en
  Tempo. Más consultas LogQL de referencia en
  [`docs/observability/loki-queries.md`](docs/observability/loki-queries.md).
- **Métricas OTel**: además de las métricas de Micrometer que ya scrapea
  Prometheus (`/actuator/prometheus`, OBS-004), el agente exporta sus propias
  métricas de runtime JVM (`target_info`, `jvm_memory_*`, con label
  `job="inventario-backend"` — coexisten con las de Micrometer sin colisionar
  porque difieren en el label `instance`).

> ⚠️ **Variable crítica**: `OTEL_EXPORTER_OTLP_PROTOCOL=grpc` es obligatoria.
> El SDK de OTel usa `http/protobuf` por defecto si no se especifica, pero el
> receptor de Alloy en el puerto `4317` es grpc-only — sin esta variable el
> agente falla en bucle con `Connection reset` al exportar. Ver "Detalle de
> OBS-001" en `CLAUDE.md` para el diagnóstico completo.

### Pipeline de observabilidad (OBS-002)

La configuración de Prometheus y del colector Grafana Alloy (`observability/prometheus/prometheus.yml`,
`observability/alloy/alloy-config.alloy`) es la pieza central que hace posible OBS-001:

- **Alloy** recibe OTLP en `4317` (gRPC) / `4318` (HTTP) y enruta con un único
  pipeline (`otelcol.receiver.otlp` → `otelcol.processor.batch`) hacia 3 exporters en
  paralelo: `otelcol.exporter.prometheus` (vía `prometheus.remote_write` →
  `http://prometheus:9090/api/v1/write`), `otelcol.exporter.otlp` (→ Tempo) y
  `otelcol.exporter.loki` (→ Loki).
- **Prometheus** habilita `--web.enable-remote-write-receiver` (requerido para recibir
  el `remote_write` de Alloy) y scrapea 3 targets: `prometheus` (self), `inventario-backend`
  (Micrometer, OBS-004) y **`alloy`** (`alloy:12345/metrics` — métricas propias del
  colector: `alloy_component_*`, `otelcol_receiver_*`, `otelcol_exporter_*`).
- El job `alloy` es **self-monitoring del pipeline de observabilidad**: permite
  detectar en Prometheus si el colector está sano (`alloy_component_controller_running_components`),
  si hay backlog en las colas de exportación (`otelcol_exporter_queue_size`) o si
  algún exporter está fallando (`otelcol_exporter_*_failed`), sin depender de que las
  trazas/logs/métricas de negocio lleguen correctamente a destino para notarlo.

Verificación rápida con el stack levantado:

```bash
curl -s http://localhost:9090/api/v1/targets   # los 3 jobs en status "up"
curl -s http://localhost:12345/metrics | head  # métricas crudas de Alloy
```

### CI/CD

#### GitHub Actions (`.github/workflows/ci.yml`)

Se ejecuta automáticamente en cada push a `develop` y en cada PR contra `develop`/`main`:

| Job | Comando | Artefacto |
|---|---|---|
| Build | `./gradlew build -x test` | — |
| Unit Tests | `./gradlew test --tests "com.inventario.unit.*" jacocoTestReport jacocoTestCoverageVerification` | Resultados XML + reporte JaCoCo |
| Integration & API Tests | `./gradlew test --tests "com.inventario.integration.*" --tests "com.inventario.api.*"` | Resultados XML |

#### Jenkins (`Jenkinsfile`)

Pipeline declarativo en la raíz del repositorio. Para usarlo en Jenkins:

1. Crear un nuevo job de tipo **Pipeline** (o Multibranch Pipeline).
2. En **Pipeline → Definition**: seleccionar *Pipeline script from SCM*.
3. SCM: Git → URL del repositorio → Branch: `*/develop`.
4. Script Path: `Jenkinsfile`.
5. **Prerrequisitos del agente Jenkins:**
   - JDK 21 configurado en *Manage Jenkins → Tools → JDK installations* con el nombre `JDK-21`.
   - Docker daemon accesible desde el agente (necesario para Testcontainers y para el stage de Build Docker Image).

Stages del pipeline:

| Stage | Descripción |
|---|---|
| Checkout | `checkout scm` + `chmod +x gradlew` |
| Build | `./gradlew build -x test` |
| Unit Tests | `./gradlew test --tests "com.inventario.unit.*" jacocoTestReport jacocoTestCoverageVerification` |
| Integration & API Tests | `./gradlew test --tests "com.inventario.integration.*" --tests "com.inventario.api.*"` |
| Build Docker Image | `docker build -t inventario-backend:${BUILD_NUMBER}` |

Post (siempre): publica resultados JUnit (`backend/build/test-results/test/*.xml`), reporte de cobertura JaCoCo (HTML Publisher) y archiva el JAR (`backend/build/libs/*.jar`).

> **JaCoCo obligatorio (TEST-001):** `jacocoTestCoverageVerification`
> (`backend/build.gradle.kts`) falla el build si `ProductService`/
> `StockService` caen por debajo de 85% de líneas / 65% de branches — hoy
> están en 100%/100% y 100%/86%, con margen de sobra. El gate está acotado
> a esas 2 clases (no a todo el proyecto) a propósito: el stage "Unit Tests"
> corre solo `com.inventario.unit.*`, y con ese filtro la cobertura global
> del proyecto cae a ~67% (repositorios/mappers/config solo se ejercitan
> con los tests de integración de TEST-002, en otro stage).

### Dockerfiles optimizados (CICD-004)

Ambas imágenes usan build multi-stage, corren como usuario no-root y exponen
`HEALTHCHECK`. No se usan en `docker-compose.dev.yml` (ahí el frontend corre con
`npm run dev`) — son el artefacto que consume el pipeline CI/CD y `docker-compose.staging.yml`
(INFRA-004, pendiente).

| Imagen | Build | Runtime | Tamaño (verificado) | Usuario |
|---|---|---|---|---|
| `backend/Dockerfile` | `eclipse-temurin:21-jdk-alpine` (Gradle `bootJar`) | `eclipse-temurin:21-jre-alpine` | ~271 MB | `spring` (no-root) |
| `frontend/Dockerfile` | `node:20-alpine` (`npm ci` + `vite build`) | `nginx:alpine` (puerto **8080**, no 80 — un proceso no-root no puede bindear puertos <1024) | ~63 MB | `nginx` (no-root, reutiliza el usuario ya presente en la imagen base) |

Build y verificación local:

```bash
docker build -t inventario-backend:dev ./backend
docker build -t inventario-frontend:dev ./frontend

docker run --rm -p 18080:8080 inventario-frontend:dev
curl http://localhost:18080/healthz   # -> ok
```

`frontend/nginx.conf` sirve el build estático (`dist/`) con fallback SPA
(`try_files ... /index.html`, necesario para las rutas de React Router como
`/products/:id/edit`) y expone `/healthz` para el `HEALTHCHECK`.

### Notas

- El backend expone métricas en `/actuator/prometheus` (Micrometer) para que
  Prometheus pueda scrapearlas. La lógica de negocio (Productos, Stock, Auditoría)
  y la seguridad OAuth2 Resource Server (SEC-001/SEC-002) están completamente
  implementadas — ver secciones anteriores para el detalle de cada módulo.
- CORS está habilitado en `SecurityConfig` para los orígenes definidos en
  `CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:5173`, el frontend Vite).
- Keycloak se inicia en modo `start-dev` con una base de datos propia (`keycloak`)
  creada automáticamente dentro de la misma instancia de PostgreSQL (ver
  `scripts/init-postgres/01-create-keycloak-db.sh`), y con `KC_HOSTNAME=localhost`
  fijo para que el claim `iss` de los tokens sea siempre
  `http://localhost:8080/realms/inventario`, sin importar si la petición al
  endpoint de token viene del host o de otro contenedor de la red Docker.
