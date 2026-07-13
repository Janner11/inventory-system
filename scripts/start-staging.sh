#!/bin/sh
# INFRA-004: orquesta el arranque secuencial completo de staging sin intervención
# manual — "sube infra, espera healthy, sube app, espera healthy" (paso 7 del ticket).
#
# Uso:
#   ./scripts/start-staging.sh              # requiere .env.staging con BACKEND_IMAGE/
#                                            # FRONTEND_IMAGE ya construidas y accesibles
#   BUILD_LOCAL=true ./scripts/start-staging.sh   # construye backend/frontend desde el
#                                            # código fuente local con esos mismos tags,
#                                            # en vez de asumir que ya existen en GHCR —
#                                            # útil para verificación local o si CICD-001
#                                            # (publicación a GHCR) todavía no corrió.
#   SEED=true ./scripts/start-staging.sh    # además corre scripts/seed-staging.sh al final
#
# Casos de error (sección "Casos de Error" del ticket):
#   - Imagen de backend/frontend no encontrada: si BUILD_LOCAL no está en "true" y la
#     imagen no existe localmente ni se puede pull-ear, `docker compose up` para esos
#     servicios falla con un error claro de Docker (no un timeout de healthcheck
#     confuso) — este script no intenta enmascararlo, solo lo detecta antes de perder
#     tiempo esperando el resto del stack.
#   - Timeout en wait-for-it.sh: los timeouts son ajustables por variable de entorno
#     (INFRA_TIMEOUT/APP_TIMEOUT abajo) sin editar el script.
#   - Docker-outside-of-Docker (CICD-002, Jenkins corriendo en un contenedor): cuando
#     "docker compose" se invoca desde un proceso que a su vez le habla al daemon REAL
#     del host via un socket montado (no un daemon anidado), los bind mounts relativos
#     ("./observability/...") se resuelven contra el directorio de trabajo del PROCESO
#     QUE LLAMA - pero si ese proceso corre dentro de un contenedor cuyo filesystem no es
#     un bind mount del host (ej. el workspace de Jenkins vive en un volumen Docker, no en
#     una ruta real del host), el daemon real no puede resolver esa ruta y el mount falla
#     ("not a directory: Are you trying to mount a directory onto a file"). Fix: exportar
#     COMPOSE_PROJECT_DIR apuntando a una ruta que SÍ sea un bind mount real del host
#     (ver el servicio "jenkins" en docker-compose.dev.yml, que monta el repo en
#     /workspace-repo para este propósito) - los demás usos de este script (local, CI)
#     no necesitan setearla, ya corren directo sobre el filesystem real del host.

set -eu

cd "$(dirname "$0")/.."

COMPOSE_FILE="docker-compose.staging.yml"
ENV_FILE=".env.staging"
COMPOSE_PROJECT_DIR="${COMPOSE_PROJECT_DIR:-$(pwd)}"
BUILD_LOCAL="${BUILD_LOCAL:-false}"
SEED="${SEED:-false}"
INFRA_TIMEOUT="${INFRA_TIMEOUT:-120}"
APP_TIMEOUT="${APP_TIMEOUT:-90}"

if [ ! -f "$ENV_FILE" ]; then
  echo "start-staging: falta ${ENV_FILE} — copia .env.staging.example y ajusta los valores" >&2
  exit 1
fi

# shellcheck disable=SC1090
. "./${ENV_FILE}"

START_TS=$(date +%s)

if [ "$BUILD_LOCAL" = "true" ]; then
  echo "start-staging: BUILD_LOCAL=true — construyendo ${BACKEND_IMAGE} y ${FRONTEND_IMAGE} desde el código fuente local"
  docker build -t "${BACKEND_IMAGE}" ./backend
  docker build \
    --build-arg VITE_API_BASE_URL="${VITE_API_BASE_URL}" \
    --build-arg VITE_KEYCLOAK_URL="${VITE_KEYCLOAK_URL}" \
    --build-arg VITE_KEYCLOAK_REALM="${VITE_KEYCLOAK_REALM}" \
    --build-arg VITE_KEYCLOAK_CLIENT_ID="${VITE_KEYCLOAK_CLIENT_ID}" \
    -t "${FRONTEND_IMAGE}" ./frontend
fi

echo "start-staging: [1/4] levantando infraestructura (postgres, keycloak, observabilidad) ..."
docker compose --project-directory "$COMPOSE_PROJECT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d \
  postgres keycloak prometheus cadvisor alertmanager alert-webhook-receiver loki tempo alloy grafana

echo "start-staging: [2/4] esperando healthchecks de infraestructura (timeout ${INFRA_TIMEOUT}s) ..."
INFRA_SERVICES="postgres keycloak prometheus alertmanager alert-webhook-receiver loki tempo"
ELAPSED=0
while [ "$ELAPSED" -lt "$INFRA_TIMEOUT" ]; do
  UNHEALTHY=""
  for svc in $INFRA_SERVICES; do
    STATUS=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "inventario-staging-${svc}" 2>/dev/null || echo "missing")
    if [ "$STATUS" != "healthy" ] && [ "$STATUS" != "no-healthcheck" ]; then
      UNHEALTHY="${UNHEALTHY} ${svc}(${STATUS})"
    fi
  done
  if [ -z "$UNHEALTHY" ]; then
    echo "start-staging: infraestructura healthy (${ELAPSED}s)"
    break
  fi
  sleep 3
  ELAPSED=$((ELAPSED + 3))
done
if [ "$ELAPSED" -ge "$INFRA_TIMEOUT" ]; then
  echo "start-staging: TIMEOUT esperando infraestructura healthy tras ${INFRA_TIMEOUT}s —${UNHEALTHY}" >&2
  docker compose --project-directory "$COMPOSE_PROJECT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=50
  exit 1
fi

echo "start-staging: [3/4] levantando aplicación (backend, frontend) ..."
docker compose --project-directory "$COMPOSE_PROJECT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d backend frontend

echo "start-staging: esperando healthchecks de aplicación (timeout ${APP_TIMEOUT}s) ..."
APP_SERVICES="backend frontend"
ELAPSED=0
while [ "$ELAPSED" -lt "$APP_TIMEOUT" ]; do
  UNHEALTHY=""
  for svc in $APP_SERVICES; do
    STATUS=$(docker inspect --format '{{.State.Health.Status}}' "inventario-staging-${svc}" 2>/dev/null || echo "missing")
    if [ "$STATUS" != "healthy" ]; then
      UNHEALTHY="${UNHEALTHY} ${svc}(${STATUS})"
    fi
  done
  if [ -z "$UNHEALTHY" ]; then
    echo "start-staging: aplicación healthy (${ELAPSED}s)"
    break
  fi
  sleep 3
  ELAPSED=$((ELAPSED + 3))
done
if [ "$ELAPSED" -ge "$APP_TIMEOUT" ]; then
  echo "start-staging: TIMEOUT esperando aplicación healthy tras ${APP_TIMEOUT}s —${UNHEALTHY}" >&2
  docker compose --project-directory "$COMPOSE_PROJECT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=80 backend frontend
  exit 1
fi

echo "start-staging: [4/4] verificando /actuator/health del backend ..."
# HEALTH_CHECK_HOST: por defecto "localhost" (funciona corriendo directo sobre el host
# real - dev local, CI). Cuando este script corre DENTRO de un contenedor que a su vez le
# habla al daemon Docker real via un socket montado (Jenkins, CICD-002), "localhost" es
# el loopback DEL PROPIO CONTENEDOR, no el host real donde el puerto quedo publicado -
# hace falta "host.docker.internal" (Docker Desktop) en ese caso, ver Jenkinsfile.
./scripts/wait-for-it.sh -t 30 -i 2 "http://${HEALTH_CHECK_HOST:-localhost}:${BACKEND_PORT:-8082}/actuator/health"

if [ "$SEED" = "true" ]; then
  echo "start-staging: sembrando datos de prueba (seed-staging.sh) ..."
  BACKEND_URL="http://localhost:${BACKEND_PORT:-8082}" \
  KEYCLOAK_URL="http://localhost:${KEYCLOAK_PORT:-8180}" \
  KEYCLOAK_REALM="${KEYCLOAK_REALM:-inventario}" \
  KEYCLOAK_CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET}" \
  ./scripts/seed-staging.sh
fi

END_TS=$(date +%s)
DURATION=$((END_TS - START_TS))
echo ""
echo "start-staging: listo en ${DURATION}s"
echo "  Backend:  http://localhost:${BACKEND_PORT:-8082}"
echo "  Frontend: http://localhost:${FRONTEND_PORT:-8090}"
echo "  Keycloak: http://localhost:${KEYCLOAK_PORT:-8180}"
echo "  Grafana:  http://localhost:${GRAFANA_PORT:-3001}"
docker compose --project-directory "$COMPOSE_PROJECT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
