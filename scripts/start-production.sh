#!/bin/sh
# INFRA-005: mismo orquestador que scripts/start-staging.sh ("sube infra, espera
# healthy, sube app, espera healthy"), adaptado a docker-compose.production.yml.
#
# Diferencia deliberada frente a start-staging.sh: NO existe ninguna variable SEED ni
# ninguna ruta de codigo que llame a scripts/seed-staging.sh - la capacidad de sembrar
# datos de prueba no esta disponible en este ambiente, no es una bandera que hay que
# recordar dejar en false (Alcance Tecnico de INFRA-005).
#
# Uso:
#   ./scripts/start-production.sh              # requiere .env.production con
#                                                # BACKEND_IMAGE/FRONTEND_IMAGE ya
#                                                # construidas y accesibles
#   BUILD_LOCAL=true ./scripts/start-production.sh   # construye backend/frontend desde
#                                                # el codigo fuente local, util para
#                                                # verificacion local
#
# Mismos casos de error que start-staging.sh (ver ese script): imagen no encontrada,
# timeout de wait-for-it.sh ajustable por variable de entorno, y Docker-outside-of-Docker
# (COMPOSE_PROJECT_DIR/HEALTH_CHECK_HOST) si este script corre dentro de un contenedor
# que le habla al daemon Docker real via un socket montado.

set -eu

cd "$(dirname "$0")/.."

COMPOSE_FILE="docker-compose.production.yml"
ENV_FILE=".env.production"
COMPOSE_PROJECT_DIR="${COMPOSE_PROJECT_DIR:-$(pwd)}"
BUILD_LOCAL="${BUILD_LOCAL:-false}"
INFRA_TIMEOUT="${INFRA_TIMEOUT:-120}"
APP_TIMEOUT="${APP_TIMEOUT:-90}"

if [ ! -f "$ENV_FILE" ]; then
  echo "start-production: falta ${ENV_FILE} — copia .env.production.example y ajusta los valores" >&2
  exit 1
fi

# shellcheck disable=SC1090
. "./${ENV_FILE}"

START_TS=$(date +%s)

if [ "$BUILD_LOCAL" = "true" ]; then
  echo "start-production: BUILD_LOCAL=true — construyendo ${BACKEND_IMAGE} y ${FRONTEND_IMAGE} desde el código fuente local"
  docker build -t "${BACKEND_IMAGE}" ./backend
  # La imagen del frontend es domain-agnostic: las variables VITE_* se inyectan en
  # runtime (docker-entrypoint.sh, frontend/Dockerfile), no en build-time, así que
  # este build ya no necesita --build-arg. docker-compose.production.yml ya las pasa
  # como "environment:" del contenedor al arrancarlo (ver este mismo .env.production)
  # — apuntar a un dominio real es solo cambiar esos valores, sin reconstruir.
  docker build -t "${FRONTEND_IMAGE}" ./frontend
fi

echo "start-production: [1/4] levantando infraestructura (postgres, keycloak, observabilidad) ..."
docker compose --project-directory "$COMPOSE_PROJECT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d \
  postgres keycloak prometheus cadvisor alertmanager alert-webhook-receiver loki tempo alloy grafana

echo "start-production: [2/4] esperando healthchecks de infraestructura (timeout ${INFRA_TIMEOUT}s) ..."
INFRA_SERVICES="postgres keycloak prometheus alertmanager alert-webhook-receiver loki tempo"
ELAPSED=0
while [ "$ELAPSED" -lt "$INFRA_TIMEOUT" ]; do
  UNHEALTHY=""
  for svc in $INFRA_SERVICES; do
    STATUS=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "inventario-production-${svc}" 2>/dev/null || echo "missing")
    if [ "$STATUS" != "healthy" ] && [ "$STATUS" != "no-healthcheck" ]; then
      UNHEALTHY="${UNHEALTHY} ${svc}(${STATUS})"
    fi
  done
  if [ -z "$UNHEALTHY" ]; then
    echo "start-production: infraestructura healthy (${ELAPSED}s)"
    break
  fi
  sleep 3
  ELAPSED=$((ELAPSED + 3))
done
if [ "$ELAPSED" -ge "$INFRA_TIMEOUT" ]; then
  echo "start-production: TIMEOUT esperando infraestructura healthy tras ${INFRA_TIMEOUT}s —${UNHEALTHY}" >&2
  docker compose --project-directory "$COMPOSE_PROJECT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=50
  exit 1
fi

echo "start-production: [3/4] levantando aplicación (backend, frontend) ..."
docker compose --project-directory "$COMPOSE_PROJECT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d backend frontend

echo "start-production: esperando healthchecks de aplicación (timeout ${APP_TIMEOUT}s) ..."
APP_SERVICES="backend frontend"
ELAPSED=0
while [ "$ELAPSED" -lt "$APP_TIMEOUT" ]; do
  UNHEALTHY=""
  for svc in $APP_SERVICES; do
    STATUS=$(docker inspect --format '{{.State.Health.Status}}' "inventario-production-${svc}" 2>/dev/null || echo "missing")
    if [ "$STATUS" != "healthy" ]; then
      UNHEALTHY="${UNHEALTHY} ${svc}(${STATUS})"
    fi
  done
  if [ -z "$UNHEALTHY" ]; then
    echo "start-production: aplicación healthy (${ELAPSED}s)"
    break
  fi
  sleep 3
  ELAPSED=$((ELAPSED + 3))
done
if [ "$ELAPSED" -ge "$APP_TIMEOUT" ]; then
  echo "start-production: TIMEOUT esperando aplicación healthy tras ${APP_TIMEOUT}s —${UNHEALTHY}" >&2
  docker compose --project-directory "$COMPOSE_PROJECT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=80 backend frontend
  exit 1
fi

echo "start-production: [4/4] verificando /actuator/health del backend ..."
./scripts/wait-for-it.sh -t 30 -i 2 "http://${HEALTH_CHECK_HOST:-localhost}:${BACKEND_PORT:-8083}/actuator/health"

END_TS=$(date +%s)
DURATION=$((END_TS - START_TS))
echo ""
echo "start-production: listo en ${DURATION}s"
echo "  Backend:  http://localhost:${BACKEND_PORT:-8083}"
echo "  Frontend: http://localhost:${FRONTEND_PORT:-8091}"
echo "  Keycloak: http://localhost:${KEYCLOAK_PORT:-8280}"
echo "  Grafana:  http://localhost:${GRAFANA_PORT:-3002}"
docker compose --project-directory "$COMPOSE_PROJECT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
