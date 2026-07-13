#!/bin/sh
# INFRA-004: siembra datos de prueba adicionales contra un staging YA desplegado, vía
# la API REST real (no SQL directo) — a diferencia de V8__insert_seed_data.sql
# (TEST-007), que siembra el dataset base (10 productos/20 movimientos) automáticamente
# en cualquier ambiente donde corran las migraciones de Flyway, incluyendo staging, sin
# necesitar este script. seed-staging.sh existe para el caso de uso distinto que pide
# este ticket: datos reproducibles para que las pruebas E2E/API/seguridad tengan un
# punto de partida conocido más allá del seed base, sin depender de qué haya quedado en
# la base de datos de corridas anteriores.
#
# Idempotente: cada producto se busca por SKU antes de crearlo, así que correr este
# script varias veces contra el mismo staging no duplica datos.
#
# Variables de entorno esperadas (con los defaults de .env.staging.example):
#   BACKEND_URL, KEYCLOAK_URL, KEYCLOAK_REALM, ADMIN_USERNAME, ADMIN_PASSWORD,
#   KEYCLOAK_CLIENT_SECRET

set -eu

BACKEND_URL="${BACKEND_URL:-http://localhost:8082}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-inventario}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin@test.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
KEYCLOAK_CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:?debe estar definida (ver .env.staging)}"

echo "seed-staging: obteniendo token de ${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM} ..."
TOKEN=$(curl -sf -X POST \
  "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=inventario-backend" \
  -d "client_secret=${KEYCLOAK_CLIENT_SECRET}" \
  -d "username=${ADMIN_USERNAME}" \
  -d "password=${ADMIN_PASSWORD}" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")

if [ -z "$TOKEN" ]; then
  echo "seed-staging: no se pudo obtener un access_token — revisa credenciales/KEYCLOAK_CLIENT_SECRET" >&2
  exit 1
fi
echo "seed-staging: token obtenido"

create_product_if_missing() {
  SKU="$1"; NAME="$2"; CATEGORY="$3"; PRICE="$4"; QTY="$5"; MIN_STOCK="$6"

  EXISTING=$(curl -sf "${BACKEND_URL}/api/products/search?q=${SKU}" \
    -H "Authorization: Bearer ${TOKEN}" \
    | python3 -c "
import json,sys
try:
    data = json.load(sys.stdin)
    items = data.get('content', data) if isinstance(data, dict) else data
    print('found' if any(p.get('sku') == '${SKU}' for p in items) else 'missing')
except Exception:
    print('missing')
")

  if [ "$EXISTING" = "found" ]; then
    echo "seed-staging: ${SKU} ya existe, se omite"
    return 0
  fi

  HTTP_CODE=$(curl -s -o /tmp/seed-staging-resp.json -w "%{http_code}" -X POST \
    "${BACKEND_URL}/api/products" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"${NAME}\",\"sku\":\"${SKU}\",\"description\":\"Producto de staging (seed-staging.sh, INFRA-004)\",\"category\":\"${CATEGORY}\",\"price\":${PRICE},\"quantity\":${QTY},\"minStock\":${MIN_STOCK}}")

  if [ "$HTTP_CODE" = "201" ]; then
    echo "seed-staging: ${SKU} creado"
  else
    echo "seed-staging: fallo creando ${SKU} (HTTP ${HTTP_CODE}) — $(cat /tmp/seed-staging-resp.json)" >&2
    exit 1
  fi
}

# Dataset conocido para pruebas E2E/API/seguridad: un producto normal, uno en alerta
# de stock bajo (para las pantallas/consultas que filtran por eso) y uno con stock alto
# (útil para pruebas de salida de stock sin quedarse sin inventario a mitad de un run).
create_product_if_missing "STAGING-SMOKE-001" "Producto Staging Normal"      "Staging" 19.99  50  10
create_product_if_missing "STAGING-SMOKE-002" "Producto Staging Stock Bajo"  "Staging" 9.50   2   10
create_product_if_missing "STAGING-SMOKE-003" "Producto Staging Stock Alto" "Staging" 149.00 5000 10

echo "seed-staging: listo"
