# Despliegue

Cómo llevar este proyecto de "código en un PR" a "sistema corriendo" — los 3
ambientes que exige la consigna (Development, Preview/Staging, Production),
los 2 últimos verificados end-to-end contra el stack real, más una guía de
lo que faltaría para un despliegue de producción real (TLS, dominio propio,
secretos gestionados — no aplicable a un proyecto académico sin
infraestructura persistente).

## Artefactos de despliegue

Cada push a `main` (`.github/workflows/ci.yml`, job `docker-push`) publica
2 imágenes Docker multi-stage, no-root, con `HEALTHCHECK` (CICD-004) a
GitHub Container Registry:

```
ghcr.io/<owner>/<repo>/backend:<sha-del-commit>   (+ tag :latest)
ghcr.io/<owner>/<repo>/frontend:<sha-del-commit>  (+ tag :latest)
```

Nunca se publica **solo** `:latest` sin un tag de versión específico junto a
él (restricción del proyecto, ver `CLAUDE.md` sección 14) — cada imagen
queda trazable a un commit exacto.

## Staging

`docker-compose.staging.yml` replica la topología completa de
`docker-compose.dev.yml` (12 servicios: postgres, keycloak, backend,
frontend, prometheus, cadvisor, alertmanager, alert-webhook-receiver, loki,
tempo, alloy, grafana) con 2 diferencias: `backend`/`frontend` corren la
imagen ya publicada (`image:`, no `build:` local) y ninguna credencial tiene
valor por defecto — si falta una variable en `.env.staging`, el arranque
falla explícitamente en vez de correr con secretos de desarrollo.

```bash
cp .env.staging.example .env.staging
# editar .env.staging con valores reales

./scripts/start-staging.sh              # usa las imágenes ya publicadas
BUILD_LOCAL=true ./scripts/start-staging.sh   # o construye desde el código local
```

**Verificado end-to-end**: arranque completo (12 contenedores) en 48
segundos, Flyway aplicando las 8 migraciones contra una base de datos nueva,
y un login OAuth2 PKCE real (Playwright) contra el Keycloak de staging.
Detalle completo, tabla de puertos, troubleshooting y la limitación conocida
de `redirectUris` en [`docs/staging.md`](staging.md).

Este es el entorno contra el que corren, en cada PR, los tests E2E
(Playwright) y el scan de seguridad baseline (OWASP ZAP) del pipeline
principal (`ci.yml`, job `staging-e2e-security`) — ver
[`docs/testing/testing-strategy.md`](testing/testing-strategy.md).

## Producción

`docker-compose.production.yml` (INFRA-005) es el tercer ambiente que exige
la consigna del proyecto (Development, Preview/Staging, Production) —
replica la misma topología de 12 servicios que staging (INFRA-004), con su
propio archivo de compose standalone, su propio rango de puertos y sin
ninguna capacidad de sembrar datos de prueba.

> **Sigue siendo un proyecto académico**: este ambiente corre en la misma
> máquina que dev/staging (sin dominio público ni certificado TLS real) —
> lo que existe de verdad es la **separación estructural** que pide la
> consigna (compose file propio, credenciales propias, sin overlap de
> puertos con los otros dos ambientes), no una infraestructura de
> producción con TLS/secretos gestionados/base de datos administrada. Esas
> siguen siendo guía, no implementación — ver la tabla de diferencias más
> abajo, columna "Producción real (guía)".

```bash
cp .env.production.example .env.production
# editar .env.production con valores reales (incluyendo KEYCLOAK_HOSTNAME/
# KEYCLOAK_ISSUER_URI/CORS_ALLOWED_ORIGINS/VITE_* — el .example trae un
# dominio simulado, inventario.example.com; para verificación local
# sobreescribir con "localhost", igual que se hizo para verificar este
# mismo archivo)

./scripts/start-production.sh                  # usa las imágenes ya publicadas
BUILD_LOCAL=true ./scripts/start-production.sh  # o construye desde el código local
```

**Verificado end-to-end** (2026-07-18, stack completo desde cero,
`BUILD_LOCAL=true`): arranque de los 12 contenedores en 259s (incluye
compilar el backend y construir la imagen del frontend localmente — con
imágenes ya publicadas, como usa por defecto, es comparable a los 48s de
staging); Flyway aplicó las 8 migraciones contra una base de datos nueva
(`inventario_production`); `/actuator/health` del backend → `UP`;
`/healthz` del frontend → `200`; **login OAuth2 PKCE real de punta a punta
con Playwright** contra el Keycloak de este ambiente, redirigiendo a
`/dashboard`; los 4 targets de Prometheus (`prometheus`, `inventario-backend`,
`alloy`, `cadvisor`) en `up`; los 4 dashboards de Grafana cargando
correctamente. **Sin datos de prueba**: el catálogo tras el arranque
contiene únicamente el seed permanente de la migración `V8` (TEST-007, el
mismo baseline que corre en dev/staging vía Flyway, no un artefacto de QA
manual) — ninguna ruta de código en `start-production.sh` puede sembrar
datos adicionales, a diferencia de `start-staging.sh` (`SEED=true`).

Igual que con staging, `keycloak/realm.json` necesitó el origen del
frontend de este ambiente (`http://localhost:8091`) agregado a
`redirectUris`/`webOrigins` de `inventario-frontend` — mismo tipo de
limitación ya conocida desde CICD-001 (staging tuvo el mismo problema con
`localhost:8090`).

### Diferencias esperadas frente a staging

| Aspecto | Staging (implementado) | Production, este proyecto (implementado) | Producción real (guía, no implementado) |
|---|---|---|---|
| Archivo de compose | `docker-compose.staging.yml` | `docker-compose.production.yml`, standalone | — |
| Seed de datos de prueba | Opcional (`SEED=true` → `seed-staging.sh`) | Ninguna ruta de código lo permite | — |
| Puertos de host | Rango propio (backend 8082, etc.) | Rango propio distinto (backend 8083, etc.) — los 3 ambientes corren a la vez sin colisión | — |
| TLS | HTTP plano (`10106` de ZAP, aceptado explícitamente) | HTTP plano | Terminación TLS en un reverse proxy (nginx/Traefik) delante del `frontend` y del `backend` — no lo resuelve ningún Dockerfile de este proyecto |
| Dominio | `localhost` fijo | `localhost` fijo (el `.example` sugiere un dominio simulado, pero la verificación real fue con `localhost`) | Dominio real — la imagen del frontend ya es domain-agnostic (ver sección siguiente); falta actualizar `KC_HOSTNAME` y `redirectUris`/`webOrigins` de `inventario-frontend` en `keycloak/realm.json`, y sumar un reverse proxy con TLS |
| Secrets | `.env.staging` con placeholders `CAMBIAR_*` | `.env.production` con placeholders `CAMBIAR_*` | Gestor de secretos real (no un `.env` en disco) — Docker Secrets, Vault, o el mecanismo del proveedor cloud elegido |
| Base de datos | Contenedor Postgres del mismo `docker-compose` | Contenedor Postgres del mismo `docker-compose` | Servicio gestionado (backups automáticos, alta disponibilidad) fuera del `docker-compose` |
| Réplicas / escalado | 1 instancia de cada servicio | 1 instancia de cada servicio | Backend sin estado (JWT, sin sesión en servidor) — escalable horizontalmente detrás de un load balancer sin cambios de código |
| Observabilidad | Alertmanager con un webhook local (`alert-webhook-receiver`, solo desarrollo) | Alertmanager con un webhook local (mismo mecanismo) | `receivers` reales (Slack/email/PagerDuty) en `observability/alertmanager/alertmanager.yml` |

### Cómo apuntar producción a un dominio real

> Sección agregada tras una pregunta directa del usuario ("¿es posible hacer
> que producción funcione con un dominio sin dañar la corrida local?"). Scope
> elegido explícitamente: dejar la configuración lista y verificada para
> cualquier dominio, sin desplegar contra un servidor real (no hay uno
> disponible en este proyecto académico) — ver "Explícitamente diferido"
> abajo para lo que sigue pendiente de un servidor real.

**El frontend ya es domain-agnostic — no hace falta reconstruir la imagen
por cambiar de dominio.** Antes de este cambio, las 4 variables `VITE_*`
(`VITE_API_BASE_URL`, `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`,
`VITE_KEYCLOAK_CLIENT_ID`) se horneaban en el bundle de JS en **build-time**
(`--build-arg`, Vite las lee vía `import.meta.env` solo durante `vite
build`) — la misma imagen publicada en GHCR para staging nunca hubiera
servido para un dominio de producción distinto sin reconstruirla con otros
build-args. Ahora se inyectan en **runtime**: `frontend/docker-entrypoint.sh`
genera `/usr/share/nginx/html/env-config.js` a partir de las variables de
entorno reales del contenedor justo antes de arrancar nginx, `index.html`
carga ese archivo antes que el bundle principal, y
`frontend/src/config/env.js` lee `window.__ENV__` con fallback a
`import.meta.env` (para que `npm run dev` en local siga funcionando exactamente
igual que antes, vía `frontend/.env`). Verificado corriendo la imagen real
con `VITE_KEYCLOAK_URL=https://auth.midominio.com` — el navegador (Playwright)
efectivamente intentó conectarse a ese host, no a un default de `localhost`.

**Los 3 pasos reales para apuntar `docker-compose.production.yml` a un
dominio real** (sin tocar dev ni staging — cada ambiente tiene su propio
`.env.*`/compose):

1. **`.env.production`** — cambiar `KEYCLOAK_HOSTNAME`, `KEYCLOAK_ISSUER_URI`,
   `CORS_ALLOWED_ORIGINS` y las 4 variables `VITE_*` de `inventario.example.com`
   al dominio real (mismo archivo, ya parametrizado — no requiere editar
   ningún compose ni Dockerfile).
2. **`keycloak/realm.json`** — agregar el dominio real a `redirectUris`/
   `webOrigins` del client `inventario-frontend` (hoy solo tiene los 3
   orígenes `localhost` de dev/staging/production, sección "Seguridad y
   Autorización" de `CLAUDE.md`) — sin esto, Keycloak rechaza el login con
   `Invalid parameter: redirect_uri`, el mismo error ya documentado para
   staging.
3. **Un reverse proxy con TLS delante de los 3 servicios** (Keycloak,
   backend, frontend) — sigue siendo el único punto realmente no resuelto.
   Ninguno de los servicios de este proyecto termina HTTPS por sí mismo; un
   dominio público real necesita algo (nginx, Caddy, Traefik) escuchando en
   `:443` con un certificado válido y reenviando a los puertos internos. No
   se implementó porque requiere un servidor con DNS apuntando a él para
   poder emitir/verificar un certificado real (Let's Encrypt u otro) — no
   hay forma de probarlo sin ese servidor.

**Explícitamente diferido (no es parte de este cambio):**

- El reverse proxy/TLS en sí (paso 3 arriba) — bloqueado por no tener un
  servidor+dominio real contra el cual verificarlo, no por falta de
  parametrización del resto del stack.
- Gestor de secretos real, base de datos gestionada, escalado horizontal —
  ya documentados como guía en la tabla de arriba, sin cambios.
- Agregar el dominio real a `keycloak/realm.json` — no se agregó un dominio
  inventado a un archivo real del proyecto; el paso 2 de arriba es la
  instrucción para cuando exista uno real.

### Pasos para un despliegue real (más allá de este proyecto académico)

1. Provisionar la infraestructura externa (dominio, TLS, gestor de secretos,
   base de datos gestionada si aplica).
2. Publicar las imágenes a un tag de versión real (`docker-push` ya las deja
   en GHCR en cada push a `main` — usar ese tag, no `:latest`, para un
   despliegue reproducible). La imagen del frontend no necesita reconstruirse
   por cambiar de dominio (ver sección anterior).
3. Adaptar `keycloak/realm.json` (o gestionar el realm directamente en el
   Keycloak de producción) con el dominio real en `redirectUris`/`webOrigins`.
4. Pasar las 4 variables `VITE_*` reales como `environment:` del contenedor
   `frontend` (ya wireadas en `docker-compose.production.yml`, solo hay que
   editar `.env.production`) — sin reconstruir ninguna imagen.
5. Desplegar `docker-compose.production.yml` (o su traducción a
   Kubernetes/ECS/lo que decida el equipo) con secrets reales en
   `.env.production`, sin ningún valor por defecto de desarrollo, detrás de
   un reverse proxy con TLS real.
6. Verificar `/actuator/health` del backend y `/healthz` del frontend antes
   de enrutar tráfico real (`scripts/wait-for-it.sh`/`scripts/start-production.sh`
   ya implementan este chequeo).
7. Confirmar que Alertmanager tiene un `receiver` real configurado antes de
   considerar el despliegue "observado" — sin esto, las 5 alertas de
   Prometheus (OBS-005) se disparan pero nadie se entera.

### Rollback

Cada imagen está taggeada con el SHA del commit que la generó — un rollback
es apuntar `BACKEND_IMAGE`/`FRONTEND_IMAGE` al tag anterior conocido-bueno y
volver a desplegar, sin reconstruir nada. Las migraciones Flyway son
aditivas e inmutables (ADR-005) — un rollback de código nunca requiere
revertir una migración ya aplicada.

## CI/CD

El pipeline completo (build → tests → SonarQube → Docker build → Trivy →
deploy a staging → E2E → security scan → push a GHCR) corre tanto en
GitHub Actions (`ci.yml`) como en Jenkins (`Jenkinsfile`, mismos comandos,
mismo orden). Detalle de cada stage, secrets requeridos y cómo reproducirlo
localmente en `CONTRIBUTING.md` y [`docs/cicd/jenkins.md`](cicd/jenkins.md) /
[`docs/cicd/sonarqube.md`](cicd/sonarqube.md).
