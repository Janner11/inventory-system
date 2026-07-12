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

# VITE_API_BASE_URL y las variables VITE_KEYCLOAK_* se hornean en el bundle en
# build-time (Vite lee import.meta.env.VITE_* solo durante "vite build", nunca en
# runtime) — .dockerignore excluye ".env" del build context (correcto: no se debe
# commitear ni copiar ese archivo), así que sin estos ARG/ENV la imagen se construía
# SIN ninguna variable VITE_* seteada y keycloak-js fallaba en runtime con
# "clientId missing" (pantalla en blanco) en cualquier despliegue real de esta
# imagen (TEST-005, encontrado al correr un login real de Playwright contra el
# contenedor — nunca antes verificado, CICD-004 solo probaba curl a "/"/"/healthz").
# Los defaults matchean docker-compose.dev.yml/.env.example; para staging/producción
# (INFRA-004) se sobreescriben con --build-arg cuando esos valores difieran.
ARG VITE_API_BASE_URL=http://localhost:8081/api
ARG VITE_KEYCLOAK_URL=http://localhost:8080
ARG VITE_KEYCLOAK_REALM=inventario
ARG VITE_KEYCLOAK_CLIENT_ID=inventario-frontend
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL
ENV VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM
ENV VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID

RUN npm run build

# ===== Etapa 2: Runtime (Nginx sirviendo el build estatico) =====
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# La imagen base ya trae un usuario "nginx" (no-root); reutilizarlo evita
# tener que crear uno nuevo y solo requiere ajustar permisos de escritura.
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/run \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid

USER nginx

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
