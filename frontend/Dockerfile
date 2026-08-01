# ===== Etapa 1: Build =====
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
# Reintento manual: "npm error Exit handler never called!" es un bug conocido de npm
# (npm/cli#4028) que aparece esporadicamente bajo I/O lento/host con carga alta - se
# encontro real corriendo este mismo build via Docker-outside-of-Docker desde el
# contenedor de Jenkins (CICD-002), con el resto del stack de desarrollo (23+
# contenedores) compitiendo por el mismo daemon Docker; nunca antes visto en
# GitHub Actions (runner dedicado, sin esa contencion). El propio comando ya es
# idempotente (mismo lockfile, misma salida esperada), asi que reintentar una vez es
# seguro.
RUN npm ci || npm ci

COPY . .

# Las variables VITE_* ya NO se hornean en build-time (a diferencia de versiones
# anteriores de este Dockerfile) — se inyectan en RUNTIME vía env-config.js, generado
# por docker-entrypoint.sh al arrancar el contenedor a partir de las variables de
# entorno reales del compose. Así la misma imagen sirve para dev/staging/production o
# un dominio real futuro con solo cambiar "environment:" en el compose, sin
# reconstruir la imagen — ver docker-entrypoint.sh y src/config/env.js.
RUN npm run build

# ===== Etapa 2: Runtime (Nginx sirviendo el build estatico) =====
# "alpine-slim" (no "alpine" a secas): la imagen "nginx:alpine" completa incluye por
# defecto modulos dinamicos que este proyecto no usa (xslt, geoip, image-filter, njs,
# acme) mas curl/ca-certificates - eso por si solo pesa ~92MB, mas que el objetivo
# completo de <50MB del ticket. "alpine-slim" es el mismo nginx sin esos modulos extra
# (~22MB base) - unico cambio necesario, nginx.conf no referencia ninguno de ellos.
FROM nginx:alpine-slim
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Genera env-config.js real a partir de las variables de entorno del contenedor antes
# de arrancar nginx — ver el propio script para el detalle completo.
COPY docker-entrypoint.sh /docker-entrypoint.sh
# Windows/NTFS no trackea el bit ejecutable de forma confiable (mismo problema ya
# documentado con .husky/commit-msg, INFRA-006) — se corrige explícitamente acá en
# vez de depender de que el archivo lo traiga del checkout.
RUN chmod +x /docker-entrypoint.sh

# La imagen base ya trae un usuario "nginx" (no-root); reutilizarlo evita
# tener que crear uno nuevo y solo requiere ajustar permisos de escritura.
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/run \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid

USER nginx

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/healthz || exit 1

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
