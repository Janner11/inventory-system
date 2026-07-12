# Guía de contribución

## Flujo de ramas y commits

Ver `CLAUDE.md` (local, no versionado) sección 7 para el detalle completo. En resumen:

- `main`/`develop` protegidas, solo merge vía PR con al menos 1 aprobación.
- Ramas: `feat/*`, `fix/*`, `chore/*`, `docs/*`, `test/*`.
- [Conventional Commits](https://www.conventionalcommits.org/): `tipo(scope): descripción en minúsculas, imperativo, sin punto final`.

## Pipeline de CI (GitHub Actions, CICD-001)

`.github/workflows/ci.yml` corre en cada push a `develop`/`main` y en cada Pull Request
contra `develop`/`main`. Jobs, en el orden en que corren:

| Job | Qué hace | Depende de |
|---|---|---|
| `check-secrets` | Detecta si `SONAR_TOKEN` está configurado (gate del job `sonarqube`). | — |
| `backend-build` | `./gradlew build -x test`. | — |
| `unit-tests` | Unit tests + JaCoCo + `jacocoTestCoverageVerification` (gate real: 85% líneas/65% branches en `ProductService`/`StockService`, TEST-001). | `backend-build` |
| `sonarqube` | `./gradlew sonar` — **solo corre si `SONAR_TOKEN` está configurado** (ver abajo). | `backend-build`, `check-secrets` |
| `integration-tests` | Tests de integración (Testcontainers — Postgres/Keycloak reales). | `backend-build` |
| `api-tests` | Tests de API (RestAssured). | `backend-build` |
| `docker-build` | Construye las imágenes Docker de backend y frontend, las guarda como artifact para los jobs siguientes (sin publicarlas a ningún registry todavía). | `backend-build` |
| `staging-e2e-security` | Despliega `docker-compose.staging.yml` con las imágenes recién construidas, corre los E2E de Playwright contra ese staging real y un scan baseline de OWASP ZAP contra el frontend desplegado. | `docker-build` |
| `docker-push` | Publica las imágenes a GHCR con el tag del SHA + `latest`. **Solo corre en un push directo a `main`** (un merge real), nunca en PRs. | todos los anteriores |

### Por qué "staging-e2e-security" es un solo job

Cada `job:` de GitHub Actions corre en una máquina virtual nueva y aislada — un
`docker compose up` hecho en un job no es alcanzable desde otro job distinto, a menos
que exista un entorno externo persistente (un servidor de staging real, que este
proyecto académico no tiene). Por eso "desplegar staging" y "correr pruebas contra ese
staging ya desplegado" viven como pasos secuenciales dentro del mismo job, no como jobs
separados — en un entorno con un staging persistente de verdad, sí se podrían separar.

### Testcontainers en CI (Docker-in-Docker)

Los runners `ubuntu-latest` de GitHub Actions ya traen Docker instalado y accesible por
defecto — **no hace falta ninguna configuración adicional** de Docker-in-Docker para que
Testcontainers funcione (a diferencia de otros CI providers como GitLab CI, que sí
requieren un servicio `docker:dind` explícito). Esto ya está verificado en este proyecto
por el job `integration-tests` (incluye `SecurityIntegrationTest`, que levanta su propio
`KeycloakContainer` + `PostgreSQLContainer` reales, TEST-002).

Si en algún momento se migra a un runner self-hosted o a otro proveedor de CI, verificar
que el socket de Docker (`/var/run/docker.sock`) sea accesible desde el contenedor que
ejecuta los tests.

## Secrets requeridos en CI

Configurar en *Settings → Secrets and variables → Actions* del repositorio de GitHub:

| Secret | Requerido | Usado por | Qué pasa si falta |
|---|---|---|---|
| `SONAR_TOKEN` | No | job `sonarqube` (`ci.yml`) | El job se omite (skip, no falla el pipeline) — no hay ningún servidor SonarQube desplegado en este proyecto todavía (CICD-003, ticket separado). |
| `SONAR_HOST_URL` | No | job `sonarqube` (`ci.yml`) | Mismo caso — solo se usa si `SONAR_TOKEN` también está configurado. |
| `GHCR_TOKEN` | No | job `docker-push` (`ci.yml`) | Se usa el `GITHUB_TOKEN` automático del workflow en su lugar (ya tiene permiso de escritura de paquetes vía `permissions: packages: write` en `ci.yml`) — un PAT manual (`GHCR_TOKEN`) solo hace falta si se necesita un token con permisos distintos a los del `GITHUB_TOKEN` por defecto. |
| `NVD_API_KEY` | No | job `dependency-check` (`security-scan.yml`, TEST-005) | El scan sigue funcionando, pero las actualizaciones de la base de datos del NVD son extremadamente lentas (rate limit público). |

Ninguno de estos secrets es estrictamente obligatorio para que el pipeline pase en
verde — los jobs que dependen de ellos están diseñados para omitirse (SonarQube) o usar
un fallback razonable (GHCR) en su ausencia, en vez de fallar el pipeline entero por una
integración externa opcional.

## Ejecutar el pipeline localmente antes de un PR

```bash
cd backend
./gradlew build -x test                                              # build
./gradlew test --tests "com.inventario.unit.*" jacocoTestReport \
  jacocoTestCoverageVerification                                     # unit tests + gate
./gradlew test --tests "com.inventario.integration.*"                # integration tests
./gradlew test --tests "com.inventario.api.*"                        # API tests

cd ..
docker build -t inventario-backend:local ./backend                   # docker build
docker build -t inventario-frontend:local ./frontend                 # docker build

cp .env.staging.example .env.staging   # editar valores, ver docs/staging.md
BUILD_LOCAL=true ./scripts/start-staging.sh
cd frontend && BASE_URL=http://localhost:8090 npx playwright test    # E2E contra staging
```

## Documentación relacionada

- [`docs/staging.md`](docs/staging.md) — entorno de staging (INFRA-004).
- [`README.md`](README.md) — badges de estado del pipeline, arranque del entorno de desarrollo.
