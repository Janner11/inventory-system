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
   | Keycloak | http://localhost:8080 | `admin` / `admin` |
   | Prometheus | http://localhost:9090 | — |
   | Grafana | http://localhost:3000 | `admin` / `admin` |

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
  scrapearlas. La lógica de negocio, seguridad OAuth2 y el resto de los endpoints se
  incorporarán en tickets posteriores (BACK-001 en adelante).
- Keycloak se inicia en modo `start-dev` con una base de datos propia (`keycloak`)
  creada automáticamente dentro de la misma instancia de PostgreSQL (ver
  `scripts/init-postgres/01-create-keycloak-db.sh`). La importación del realm
  `inventario` se realizará en el ticket SEC-001.
