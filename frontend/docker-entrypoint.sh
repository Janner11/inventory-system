#!/bin/sh
# Genera env-config.js con los valores REALES de las variables de entorno del
# contenedor (docker-compose "environment:") antes de arrancar nginx — así la misma
# imagen sirve para cualquier dominio (dev/staging/production, o un dominio real
# futuro) sin reconstruirla. Ver src/config/env.js (cómo se consume en el frontend)
# y docs/deployment.md ("Cómo apuntar producción a un dominio real").
#
# Los defaults de abajo (":-http://localhost:8081/api", etc.) son solo una red de
# seguridad si alguien corre la imagen sin pasar estas variables — coinciden con los
# valores de docker-compose.dev.yml/.env.example, nunca deben usarse en staging o
# production reales (esos compose ya las pasan explícitamente).
set -e

cat > /usr/share/nginx/html/env-config.js <<EOF
window.__ENV__ = {
  VITE_API_BASE_URL: "${VITE_API_BASE_URL:-http://localhost:8081/api}",
  VITE_KEYCLOAK_URL: "${VITE_KEYCLOAK_URL:-http://localhost:8080}",
  VITE_KEYCLOAK_REALM: "${VITE_KEYCLOAK_REALM:-inventario}",
  VITE_KEYCLOAK_CLIENT_ID: "${VITE_KEYCLOAK_CLIENT_ID:-inventario-frontend}"
};
EOF

exec "$@"
