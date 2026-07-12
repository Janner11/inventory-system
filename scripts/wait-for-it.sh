#!/bin/sh
# INFRA-004: espera a que un endpoint HTTP responda 2xx/3xx (por defecto) o a que un
# puerto TCP acepte conexiones (--tcp), antes de continuar. Usado por start-staging.sh
# para esperar los healthchecks de Postgres/Keycloak/backend/frontend desde fuera de
# Docker (los healthchecks de docker-compose.staging.yml ya orquestan el orden interno
# vía `depends_on: condition: service_healthy`; este script es el equivalente para
# scripts de shell — CI, verificación local — que necesitan saber cuándo el stack
# completo ya está arriba antes de correr pruebas E2E/API/seguridad contra él).
#
# Uso:
#   wait-for-it.sh http://localhost:8082/actuator/health
#   wait-for-it.sh --tcp localhost:5433
#   wait-for-it.sh -t 180 -i 5 http://localhost:8090/healthz

set -eu

TIMEOUT=120
INTERVAL=3
MODE=http
TARGET=""

usage() {
  echo "Uso: $0 [-t timeout_segundos] [-i intervalo_segundos] [--tcp] <url|host:puerto>" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    -t) TIMEOUT="$2"; shift 2 ;;
    -i) INTERVAL="$2"; shift 2 ;;
    --tcp) MODE=tcp; shift ;;
    -h|--help) usage ;;
    *) TARGET="$1"; shift ;;
  esac
done

[ -n "$TARGET" ] || usage

check_http() {
  # -f: falla (sin cuerpo) en 4xx/5xx: solo 2xx/3xx cuentan como "listo".
  curl -sf -o /dev/null "$TARGET"
}

check_tcp() {
  HOST=$(echo "$TARGET" | cut -d: -f1)
  PORT=$(echo "$TARGET" | cut -d: -f2)
  (exec 3<>"/dev/tcp/${HOST}/${PORT}") 2>/dev/null
}

echo "wait-for-it: esperando ${TARGET} (modo=${MODE}, timeout=${TIMEOUT}s, intervalo=${INTERVAL}s)"

ELAPSED=0
while [ "$ELAPSED" -lt "$TIMEOUT" ]; do
  if [ "$MODE" = "http" ]; then
    if check_http; then
      echo "wait-for-it: ${TARGET} listo (${ELAPSED}s)"
      exit 0
    fi
  else
    if check_tcp; then
      echo "wait-for-it: ${TARGET} listo (${ELAPSED}s)"
      exit 0
    fi
  fi
  sleep "$INTERVAL"
  ELAPSED=$((ELAPSED + INTERVAL))
done

echo "wait-for-it: TIMEOUT esperando ${TARGET} tras ${TIMEOUT}s" >&2
exit 1
