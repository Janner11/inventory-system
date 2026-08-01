# Entorno de Staging (INFRA-004)

`docker-compose.staging.yml` replica la topología completa de
`docker-compose.dev.yml` (postgres, keycloak, backend, frontend, prometheus,
cadvisor, alertmanager, alert-webhook-receiver, loki, tempo, alloy, grafana —
los mismos servicios y archivos de configuración de observabilidad, mismo
`keycloak/realm.json`, ADR-008) con dos diferencias deliberadas:

1. **`backend`/`frontend` usan `image:` en vez de `build:`** — staging corre
   el artefacto ya construido y publicado (`ghcr.io/${GITHUB_REPOSITORY}/backend:${VERSION}`,
   `.../frontend:${VERSION}`), no el código fuente local. Esto es lo que
   permite que las pruebas de integración, API, E2E y seguridad corran
   "contra el sistema desplegado", no contra un build local (contexto de
   negocio del ticket).
2. **Sin valores por defecto para credenciales/URLs** — a diferencia de
   `docker-compose.dev.yml` (`${POSTGRES_PASSWORD:-inventario_pass}`),
   `docker-compose.staging.yml` usa `${VAR}` sin fallback para todo lo
   sensible. Si falta `.env.staging`, el arranque falla explícitamente en
   vez de correr con secretos de desarrollo.

## Arranque rápido

```bash
cp .env.staging.example .env.staging
# editar .env.staging con valores reales (ver "Variables de entorno" abajo)

# Opción A: las imágenes ya existen en GHCR (o localmente con ese tag)
./scripts/start-staging.sh

# Opción B: construir las imágenes desde el código fuente local primero
# (verificación local, o si el pipeline de publicación a GHCR — CICD-001 —
# todavía no corrió)
BUILD_LOCAL=true ./scripts/start-staging.sh

# Opción C: además de levantar todo, sembrar datos de prueba conocidos
SEED=true ./scripts/start-staging.sh
```

`start-staging.sh` orquesta el arranque secuencial completo sin intervención
manual (Validaciones del ticket):

1. Levanta la infraestructura (postgres, keycloak, prometheus, cadvisor,
   alertmanager, alert-webhook-receiver, loki, tempo, alloy, grafana).
2. Espera a que la infraestructura esté `healthy` (timeout configurable vía
   `INFRA_TIMEOUT`, default 120s).
3. Levanta `backend` y `frontend`.
4. Espera a que estén `healthy` (timeout configurable vía `APP_TIMEOUT`,
   default 90s).
5. Verifica `GET /actuator/health` del backend con `wait-for-it.sh`.
6. (Opcional, `SEED=true`) corre `scripts/seed-staging.sh`.

**Verificado localmente (2026-07-12):** arranque completo (12 contenedores,
todos `healthy` salvo `prometheus`/`grafana`/`alloy` — igual que en dev,
esas 3 imágenes no traen `wget`/`curl` para un `HEALTHCHECK` `CMD-SHELL`) en
**48 segundos**, muy por debajo del límite de 3 minutos del ticket. Backend
respondiendo `{"status":"UP"}` en `/actuator/health` con Flyway habiendo
aplicado las 8 migraciones (incluyendo el seed base de TEST-007) contra una
base de datos `inventario_staging` completamente nueva. Frontend
respondiendo `200` en `/` y `ok` en `/healthz`. Login real (PKCE completo)
contra el Keycloak de staging, con el dashboard renderizando los permisos
correctos del usuario — confirma que Keycloak, el backend y el frontend
funcionan juntos de punta a punta, no solo que cada contenedor individual
está `healthy`.

## Puertos

Por defecto, staging usa un rango de puertos **distinto** al de
`docker-compose.dev.yml`, para poder levantar ambos stacks en la misma
máquina sin colisión (útil para verificación local; en CI, un runner
efímero, esto no importa):

| Servicio | Puerto dev | Puerto staging (default) |
|---|---|---|
| Backend | 8081 | 8082 |
| Frontend | — (Vite 5173 en dev) | 8090 |
| Postgres | 5432 | 5434 |
| Keycloak | 8080 | 8180 |
| Prometheus | 9090 | 9091 |
| Grafana | 3000 | 3001 |
| Alertmanager | 9093 | 9094 |
| Alert webhook receiver | 5001 | 5002 |
| Loki | 3100 | 3101 |
| Tempo | 3200 | 3201 |
| Alloy (OTLP gRPC/HTTP/UI) | 4317/4318/12345 | 4417/4418/12346 |
| cAdvisor | 8085 | 8086 |

Todos son configurables vía `.env.staging` si un pipeline de CI necesita
otros valores.

## Variables de entorno

Ver [`.env.staging.example`](../.env.staging.example) para el listado
completo con comentarios. Puntos clave:

- **`GITHUB_REPOSITORY`/`VERSION`/`BACKEND_IMAGE`/`FRONTEND_IMAGE`**: el
  ticket pide literalmente `image: ghcr.io/${GITHUB_REPOSITORY}/backend:${VERSION}`.
  `BACKEND_IMAGE`/`FRONTEND_IMAGE` ya vienen compuestos en el `.env.staging.example`
  (interpolación de variables dentro del propio archivo `.env`, soportada por
  Docker Compose v2) para poder apuntarlos también a un tag local
  (`docker build -t <mismo-tag> ...`) durante verificación, sin depender de
  acceso real a GHCR.
- **`KEYCLOAK_HOSTNAME`**: a diferencia de dev (que fija `KC_HOSTNAME=localhost`),
  en staging es una variable — debe apuntar al host público real donde
  corre staging (o a `localhost` para verificación local).
- **`VITE_*`**: se inyectan en **runtime**, no en build-time — `docker-compose.staging.yml`
  las pasa como `environment:` del contenedor `frontend`,
  `frontend/docker-entrypoint.sh` las lee al arrancar y genera
  `env-config.js` con esos valores antes de que nginx empiece a servir (ver
  `frontend/src/config/env.js`). La imagen `FRONTEND_IMAGE` es la misma para
  cualquier dominio — apuntar a un dominio distinto es cambiar estos 4
  valores en `.env.staging`, sin reconstruir la imagen.
- **`SPRING_PROFILES_ACTIVE=staging`**: fijo en `docker-compose.staging.yml`
  (no configurable). **Decisión explícita**: no se creó un
  `backend/src/main/resources/application-staging.yml` — `application.yml`
  ya parametriza el 100% de su configuración vía variables de entorno (sin
  ningún bloque `spring.config.activate.on-profile`), así que un
  `application-staging.yml` vacío no tendría ningún efecto funcional; el
  perfil "staging" simplemente no encuentra un documento propio y usa la
  configuración base, que ya es la correcta para cualquier ambiente.

## Cómo construir las imágenes

```bash
# Backend
docker build -t "${BACKEND_IMAGE}" ./backend

# Frontend (domain-agnostic — las VITE_* se inyectan en runtime, no acá)
docker build -t "${FRONTEND_IMAGE}" ./frontend
```

`BUILD_LOCAL=true ./scripts/start-staging.sh` hace exactamente esto antes de
levantar el stack — útil mientras CICD-001 (publicación a GHCR) no esté
implementado, o para verificación local sin tocar el registry real.

## Scripts

| Script | Uso |
|---|---|
| `scripts/start-staging.sh` | Orquesta el arranque secuencial completo (ver arriba). |
| `scripts/wait-for-it.sh` | Espera un endpoint HTTP (200-399) o un puerto TCP antes de continuar — usado internamente por `start-staging.sh` y reutilizable desde CI para esperar el stack antes de correr pruebas E2E/API/seguridad contra él. |
| `scripts/seed-staging.sh` | Siembra 3 productos de prueba conocidos (`STAGING-SMOKE-001/002/003` — uno normal, uno en alerta de stock bajo, uno con stock alto) vía la API REST real, idempotente (busca por SKU antes de crear). Complementa, no duplica, el seed base de Flyway (`V8__insert_seed_data.sql`, TEST-007) que ya siembra automáticamente en cualquier ambiente donde corran las migraciones. |

## Errores comunes

- **`Bind for 0.0.0.0:PUERTO failed: port is already allocated`**: algún
  otro proceso/contenedor ya usa ese puerto en la máquina. Cambiar el
  puerto en `.env.staging` (ninguno de los defaults de este archivo es
  especial, todos son configurables).
- **Imagen de backend/frontend no encontrada** (`pull access denied` /
  `manifest unknown`): la imagen no existe en GHCR con ese tag, o no hay
  credenciales para el registry (`docker login ghcr.io`). Verificar que
  CICD-001 ya publicó esa versión, o usar `BUILD_LOCAL=true` para construirla
  desde el código fuente local en su lugar.
- **Timeout en `wait-for-it.sh`/`start-staging.sh`**: ajustar
  `INFRA_TIMEOUT`/`APP_TIMEOUT` (variables de entorno del script) si la
  máquina/runner es más lento que lo esperado; revisar
  `docker compose -f docker-compose.staging.yml logs <servicio>` para la
  causa real antes de solo alargar el timeout.
- **Login falla con `Invalid parameter: redirect_uri`**: el cliente
  `inventario-frontend` de `keycloak/realm.json` solo permite los orígenes
  ya listados en `redirectUris`/`webOrigins` (por defecto,
  `localhost:5173` para dev y `localhost:8090` para staging local). Si se
  cambia `FRONTEND_PORT` o se despliega staging en un dominio real, hay que
  agregar ese origen al realm — ver "Limitación conocida" abajo.

## Limitación conocida: origen de Keycloak fijo en el realm

`keycloak/realm.json` (fuente única de verdad, ADR-008) tiene
`redirectUris`/`webOrigins` fijos a `http://localhost:5173` (dev) y
`http://localhost:8090` (staging local, agregado en este ticket) para el
cliente `inventario-frontend`. **Encontrado durante la verificación de este
ticket**: antes de agregar el segundo origen, el login contra staging
fallaba con `Invalid parameter: redirect_uri` (confirmado con Playwright
contra el stack real) — Keycloak rechaza cualquier `redirect_uri` no
listado explícitamente. Si staging se despliega en un dominio real (no
`localhost`), ese dominio necesita agregarse al realm de la misma forma. No
se implementó un mecanismo de templating por ambiente (fuera de alcance de
este ticket) — es un punto a resolver si el proyecto llega a desplegarse en
un dominio real.

## Detener staging

```bash
docker compose --env-file .env.staging -f docker-compose.staging.yml down     # conserva volúmenes/datos
docker compose --env-file .env.staging -f docker-compose.staging.yml down -v  # borra también los volúmenes
```
