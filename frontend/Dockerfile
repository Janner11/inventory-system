# ===== Etapa 1: Build =====
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# VITE_API_BASE_URL y las variables VITE_KEYCLOAK_* se hornean en el bundle en
# build-time (Vite). En staging/produccion se pasan como --build-arg / ARG si
# difieren del .env usado en desarrollo (ver INFRA-004).
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
