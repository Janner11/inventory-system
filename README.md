# Sistema de Gestión de Inventarios Empresarial

Proyecto académico (PUCMM — Aseguramiento de Calidad de Software) para la gestión de
inventarios de pequeñas empresas. Monorepo compuesto por un frontend en React (Vite) y
un backend en Spring Boot 3 (Java 21), con autenticación vía Keycloak y observabilidad
basada en Prometheus y Grafana.

## Estructura del repositorio

```
frontend/   → SPA en React + Vite
backend/    → API REST en Spring Boot 3 (Java 21)
observability/ → configuración de Prometheus y Grafana
scripts/    → scripts auxiliares (init de base de datos, etc.)
keycloak/   → configuración/exportación del realm
```

## Entorno de desarrollo local (Docker Compose)

El archivo [`docker-compose.dev.yml`](docker-compose.dev.yml) levanta toda la
infraestructura necesaria para el desarrollo local con un solo comando:

- **PostgreSQL 16** — base de datos de la aplicación y de Keycloak
- **Keycloak 24** — Identity & Access Management (IAM)
- **Backend** — API REST Spring Boot (build local desde `backend/Dockerfile`)
- **Prometheus** — recolección de métricas
- **Grafana** — dashboards y visualización

Todos los servicios se conectan a través de la red `inventario-network` y persisten
sus datos en volúmenes de Docker (`postgres_data`, `keycloak_data`, `prometheus_data`,
`grafana_data`).

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
   | Grafana | http://localhost:3000 | `admin` / `admin` |

### Keycloak — realm `inventario` (SEC-001)

El realm `inventario` se importa automáticamente al levantar Keycloak desde
[`keycloak/realm.json`](keycloak/realm.json) (flag `--import-realm`). Incluye:

- **Clients:** `inventario-frontend` (público, PKCE, redirect `http://localhost:5173/*`)
  y `inventario-backend` (confidencial, secret de dev `inventario-backend-secret`,
  `directAccessGrantsEnabled=true` para pruebas con `curl`).
- **Permisos (client roles en `inventario-backend`):** `product:view`, `product:manage`.
- **Usuarios de prueba:**

  | Usuario | Password | Permisos |
  |---------|----------|----------|
  | `admin@test.com` | `admin123` | `product:view`, `product:manage` |
  | `viewer@test.com` | `viewer123` | `product:view` |

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

5. Para detener y eliminar los contenedores (los datos persisten en los volúmenes):

   ```bash
   docker compose -f docker-compose.dev.yml down
   ```

   Para eliminar también los volúmenes (reinicio completo de datos):

   ```bash
   docker compose -f docker-compose.dev.yml down -v
   ```

### Notas

- El backend actual es un esqueleto mínimo de Spring Boot (endpoint `/api/ping` y
  Actuator) que expone métricas en `/actuator/prometheus` para que Prometheus pueda
  scrapearlas. La lógica de negocio (Productos, Stock, etc.) se incorporará en
  tickets posteriores (BACK-002 en adelante). La seguridad OAuth2 Resource Server
  (SEC-001/SEC-002) ya está implementada — ver sección anterior.
- CORS está habilitado en `SecurityConfig` para los orígenes definidos en
  `CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:5173`, el frontend Vite).
- Keycloak se inicia en modo `start-dev` con una base de datos propia (`keycloak`)
  creada automáticamente dentro de la misma instancia de PostgreSQL (ver
  `scripts/init-postgres/01-create-keycloak-db.sh`), y con `KC_HOSTNAME=localhost`
  fijo para que el claim `iss` de los tokens sea siempre
  `http://localhost:8080/realms/inventario`, sin importar si la petición al
  endpoint de token viene del host o de otro contenedor de la red Docker.
