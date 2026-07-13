# Despliegue

Cómo llevar este proyecto de "código en un PR" a "sistema corriendo" —
staging (real, verificado end-to-end) y producción (guía basada en los
mismos artefactos, ya que este es un proyecto académico sin un entorno de
producción persistente).

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

Este proyecto es un trabajo académico (PUCMM, Aseguramiento de Calidad de
Software) y **no tiene un entorno de producción real desplegado** — no hay
dominio público, certificado TLS ni servidor persistente fuera de las
máquinas de desarrollo/CI. Lo que sigue es la guía de cómo desplegarlo si
existiera uno, usando exactamente los mismos artefactos que staging (mismas
imágenes de GHCR, mismo `docker-compose.staging.yml` como plantilla) — no
un pipeline ni una infraestructura distinta a mantener en paralelo.

### Diferencias esperadas frente a staging

| Aspecto | Staging (implementado) | Producción (guía) |
|---|---|---|
| TLS | HTTP plano (`10106` de ZAP, aceptado explícitamente en dev/staging) | Terminación TLS en un reverse proxy (nginx/Traefik) delante del `frontend` y del `backend` — no lo resuelve ningún Dockerfile de este proyecto |
| Dominio | `localhost` fijo (`KC_HOSTNAME`, `redirectUris` de Keycloak) | Dominio real — requiere actualizar `KC_HOSTNAME`, `redirectUris`/`webOrigins` de `inventario-frontend` en `keycloak/realm.json`, y las 4 variables `VITE_*` como build-args del frontend (ver [`docs/security/keycloak.md`](security/keycloak.md)) |
| Secrets | `.env.staging` con placeholders `CAMBIAR_*` | Gestor de secretos real (no un `.env` en disco) — Docker Secrets, Vault, o el mecanismo del proveedor cloud elegido |
| Base de datos | Contenedor Postgres del mismo `docker-compose` | Servicio gestionado (backups automáticos, alta disponibilidad) fuera del `docker-compose` |
| Réplicas / escalado | 1 instancia de cada servicio | Backend sin estado (JWT, sin sesión en servidor) — escalable horizontalmente detrás de un load balancer sin cambios de código |
| Observabilidad | Alertmanager con un webhook local (`alert-webhook-receiver`, solo desarrollo) | `receivers` reales (Slack/email/PagerDuty) en `observability/alertmanager/alertmanager.yml` |

### Pasos (basados en el mismo mecanismo de staging)

1. Provisionar la infraestructura externa (dominio, TLS, gestor de secretos,
   base de datos gestionada si aplica).
2. Publicar las imágenes a un tag de versión real (`docker-push` ya las deja
   en GHCR en cada push a `main` — usar ese tag, no `:latest`, para un
   despliegue reproducible).
3. Adaptar `keycloak/realm.json` (o gestionar el realm directamente en el
   Keycloak de producción) con el dominio real en `redirectUris`/`webOrigins`.
4. Construir el frontend con los `--build-arg VITE_*` apuntando a las URLs
   reales de producción (`frontend/Dockerfile`, ARGs ya parametrizados desde
   TEST-005/CICD-004).
5. Desplegar con una variante de `docker-compose.staging.yml` (o su
   traducción a Kubernetes/ECS/lo que decida el equipo) con secrets reales,
   sin ningún valor por defecto de desarrollo.
6. Verificar `/actuator/health` del backend y `/healthz` del frontend antes
   de enrutar tráfico real (`scripts/wait-for-it.sh` ya implementa este
   chequeo, reutilizable).
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
