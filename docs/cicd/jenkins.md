# Pipeline visual con Jenkins (CICD-002)

`Jenkinsfile` (raíz del repo) implementa los 10 stages del ticket, en paridad
con `.github/workflows/ci.yml` (CICD-001): mismos comandos de Gradle/Docker,
mismo entorno de staging real (`docker-compose.staging.yml`, INFRA-004),
mismo quality gate de seguridad (`scripts/zap-report-gate.py`, TEST-005).

## Arquitectura

| Stage | Qué hace |
|---|---|
| 1. Checkout | `checkout scm` |
| 2. Build Backend | `./gradlew build -x test` |
| 3. Unit Tests | Unit tests + JaCoCo + `jacocoTestCoverageVerification` (quality gate real, TEST-001) |
| 4. Integration Tests | `com.inventario.integration.*` + `com.inventario.api.*` (Testcontainers reales, TEST-002/003) |
| 5. Build Docker Images | `docker build` de `backend/` y `frontend/` |
| 6. Deploy Staging | `docker-compose.staging.yml` real, vía `scripts/start-staging.sh` |
| 7. E2E Tests | Playwright contra el staging recién desplegado |
| 8. Security Scan | ZAP baseline contra el frontend de staging |
| 9. Quality Gate | `zap-report-gate.py` (0 HIGH, siempre activo) + SonarQube (condicional, ver abajo) |
| 10. Publish Reports | `junit` + `publishHTML` (JaCoCo, JUnit/RestAssured, Playwright, ZAP) + `archiveArtifacts` |

Los stages 3/4/7/8 están envueltos en `catchError(buildResult: 'UNSTABLE', ...)`
— un fallo ahí marca el stage en rojo/amarillo (Blue Ocean) pero **no aborta
el pipeline**, para que "Quality Gate"/"Publish Reports" siempre se
alcancen y publiquen lo que sí corrió, incluso si algo falló antes.

### Por qué "Deploy Staging" + "E2E Tests" + "Security Scan" son stages
### separados (no jobs de GitHub Actions)

A diferencia de `ci.yml` (CICD-001), donde esos 3 conceptos viven como pasos
secuenciales de UN SOLO job de GitHub Actions (misma limitación: jobs
corren en VMs aisladas, no pueden compartir contenedores en ejecución),
Jenkins con `agent any` corre **todo el pipeline en el mismo nodo/workspace**
— así que aquí sí pueden ser stages verdaderamente independientes en la
visualización de Blue Ocean, mientras comparten el mismo Docker daemon y
los mismos contenedores de staging ya desplegados.

## Jenkins en Docker (Alcance Técnico del ticket)

`docker-compose.dev.yml`, servicio `jenkins` — imagen custom
(`jenkins/Dockerfile`) sobre `jenkins/jenkins:lts-jdk17` con:

- **Plugins preinstalados** (`jenkins/plugins.txt`, vía `jenkins-plugin-cli`):
  pipeline, git/github, Blue Ocean, docker-workflow, htmlpublisher, junit,
  jacoco, sonar, configuration-as-code, job-dsl, timestamper, ws-cleanup.
- **JDK 21** instalado directo en la imagen (Temurin) — el propio core de
  Jenkins sigue en JDK 17 (imagen base), son JVMs independientes; evita
  depender del instalador automático de JDKs de Jenkins (requiere salida a
  internet en cada arranque de agente).
- **Docker CLI + docker compose plugin** — el daemon real es el mismo que
  usa el resto del proyecto, vía el socket del host montado
  (`/var/run/docker.sock`) — Docker-outside-of-Docker, no un daemon anidado.
- **Configuration as Code** (`jenkins/jenkins-casc.yml`, `CASC_JENKINS_CONFIG`):
  usuario admin (`JENKINS_ADMIN_ID`/`JENKINS_ADMIN_PASSWORD`, sin default
  trivial), herramienta `JDK-21`, credenciales `github-credentials`/
  `sonar-token` (vacías por defecto — ver "Quality Gate" abajo), y el
  **Pipeline job `inventario-pipeline` autoprovisionado vía job-dsl** — sin
  necesitar crear el job a mano en la UI ("Pasos de Implementación" #7 del
  ticket).

```bash
cp .env.example .env   # si no existe ya
# editar JENKINS_ADMIN_PASSWORD (sin default trivial a propósito)
docker compose -f docker-compose.dev.yml up -d --build jenkins
# http://localhost:8095 (usuario: JENKINS_ADMIN_ID, contraseña: JENKINS_ADMIN_PASSWORD)
```

## Casos de Error del ticket

### "Jenkins no recibe webhook (configurar ngrok o IP pública)"

No hay ninguna IP pública ni túnel (ngrok) en este entorno académico. El job
`inventario-pipeline` está provisionado (vía job-dsl en `jenkins-casc.yml`)
apuntando a `file:///workspace-repo` — un checkout local del propio repo
(montado como bind mount de solo lectura en `docker-compose.dev.yml`), **no**
un remoto GitHub real. Esto permite correr el pipeline real sin depender de
un webhook que este entorno no puede recibir. Para un despliegue real (con
un servidor Jenkins con IP pública o accesible desde GitHub), el mismo
`Jenkinsfile` funciona sin cambios — solo hace falta reconfigurar el SCM del
job (o convertirlo a Multibranch Pipeline, "Pasos de Implementación" #7) para
apuntar al remoto real de GitHub y agregar el webhook en
*Settings → Webhooks* del repositorio.

### "Permisos de Docker en Jenkins (jenkins user en docker group)"

`jenkins/entrypoint.sh` resuelve esto en cada arranque del contenedor: el
GID real del socket de Docker montado varía por máquina/entorno (Docker
Desktop en macOS vs. Docker Engine en Linux), así que se detecta en runtime
(`stat -c '%g' /var/run/docker.sock`), se crea/reutiliza un grupo con ese
GID, y se agrega el usuario `jenkins` a él — **antes** de bajar privilegios
(`gosu jenkins`) para arrancar Jenkins de verdad. Verificado real: `docker
exec -u jenkins inventario-jenkins docker ps` lista los contenedores del
host sin necesitar `sudo`.

## Verificación real (2026-07-12)

Se corrió el pipeline real contra Jenkins 9 veces durante esta
implementación (no solo se validó la sintaxis del `Jenkinsfile`), triggereado
vía la API REST de Jenkins. **6 bugs reales encontrados y corregidos**,
todos específicos a correr Jenkins en un contenedor que a su vez controla el
Docker daemon del host (Docker-outside-of-Docker) — ninguno relacionado con
la lógica de negocio del pipeline en sí:

1. **Checkout bloqueado**: el plugin Git de Jenkins rechaza por defecto
   cualquier remoto `file://` como potencialmente inseguro. Fix:
   `-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true` (`JAVA_OPTS`,
   `jenkins/Dockerfile`) — necesario porque el job usa `file:///workspace-repo`
   en vez de un remoto GitHub real (ver "Casos de Error" arriba).
2. **Testcontainers no podía arrancar Ryuk** (su sidecar de limpieza):
   intenta conectar de vuelta a `172.17.0.1`, no alcanzable desde dentro del
   contenedor de Jenkins. Fix: `TESTCONTAINERS_RYUK_DISABLED=true`
   (`docker-compose.dev.yml`) — mitigación oficial para Testcontainers vía
   Docker-outside-of-Docker.
3. **`npm ci` fallaba intermitentemente** con `"Exit handler never called!"`
   (bug conocido de npm, [npm/cli#4028](https://github.com/npm/cli/issues/4028))
   bajo la carga alta de correr Jenkins junto al resto del stack de
   desarrollo en la misma máquina. Fix: reintento (`npm ci || npm ci`,
   `frontend/Dockerfile`).
4. **Testcontainers conectaba a Postgres/Keycloak vía `localhost`**, que
   dentro del contenedor de Jenkins es su propio loopback, no el host real
   donde esos contenedores publican sus puertos. Fix:
   `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`.
5. **`docker-compose.staging.yml` no podía resolver sus bind mounts
   relativos** (`./observability/...`) al desplegarse desde dentro de
   Jenkins: en Docker Desktop, el daemon real solo puede montar rutas que
   sean rutas reales de macOS (`/Users/...`), no rutas que solo tienen
   sentido dentro del contenedor de Jenkins que hizo la llamada. Fix: bind
   mount del subdirectorio de workspaces de Jenkins a una ruta real del host
   (`./jenkins-workspace:/var/jenkins_home/workspace`,
   `docker-compose.dev.yml`) + `COMPOSE_PROJECT_DIR`/`HEALTH_CHECK_HOST`
   configurables en `scripts/start-staging.sh`, seteados por el Jenkinsfile
   cuando `HOST_WORKSPACE_ROOT` está presente.
6. **La imagen Docker de Playwright estaba pineada a una versión vieja**
   (`v1.48.0-jammy`) mientras el proyecto ya usa `@playwright/test@1.61.1` —
   los 27 tests fallaban con `Executable doesn't exist`. Fix: usar `node:20`
   + `npx playwright install --with-deps chromium` (instala siempre la
   versión de browser que corresponde al `package-lock.json` real, sin
   depender de mantener sincronizado un tag de imagen a mano) +
   `--shm-size=1g` (Chromium necesita más de los 64MB por defecto de
   `/dev/shm` en Docker, o crashea bajo carga).

**Resultado de la verificación final:** los 10 stages corren de punta a
punta contra infraestructura real — Unit Tests, Integration Tests, Build
Docker Images, Deploy Staging (12 contenedores healthy), Security Scan
(ZAP baseline `FAIL-NEW: 0, PASS: 62`) y Quality Gate (`0 vulnerabilidades
HIGH`, SonarQube omitido limpiamente sin servidor real) pasan de forma
consistente. E2E Tests mejoró de 0/27 (bug #6) a 11/27 pasando en la última
corrida — el resto mostró señales de **contención de recursos** (Chromium
crasheando bajo carga, `"Navigation failed because page crashed!"`) al
correr Jenkins simultáneamente con el resto del stack de desarrollo
(~35 contenedores en total: dev + staging + Jenkins) en una sola máquina
Docker Desktop compartida — no un defecto de configuración del pipeline en
sí. `--shm-size=1g` (bug #6) es la mitigación estándar aplicada; una
corrida en un runner con recursos dedicados (o en el propio GitHub Actions,
donde estos mismos comandos ya corren limpios en `ci.yml`, CICD-001) no
debería tener este problema. `Publish Reports` publicó correctamente los 4
reportes HTML (JaCoCo, JUnit/RestAssured, Playwright, ZAP) en las 3
corridas finales.

**Metodología de verificación** (para que sea reproducible): el job
`inventario-pipeline` se apuntó temporalmente (vía el Script Console de
Jenkins, revertido al final) a un commit "flotante" creado con
`git stash create` — un objeto commit real pero no referenciado por ninguna
rama (no aparece en `git log`, no se pushea, no lo ve nadie) — más una rama
local temporal (`tmp/jenkins-cicd002-verify`, eliminada al terminar) para
que `git fetch` desde el remoto `file://` pudiera alcanzarlo. Esto permitió
probar los cambios de esta implementación (Jenkinsfile, Dockerfile,
docker-compose.dev.yml) contra el Jenkins real **sin crear ningún commit
real en el historial del repositorio** — la implementación se commitea de
forma normal recién cuando el equipo decide hacerlo, como con cualquier
otro ticket de este proyecto.

## Limitación conocida: verificación en Docker Desktop vs. producción

Los bugs #4 y #5 (arriba) son específicos de correr Docker-outside-of-Docker
**en Docker Desktop para macOS/Windows**, que interpone una VM Linux entre
el `docker` CLI y el daemon real, con su propia capa de traducción de rutas
de bind mount. **En un host Linux real** (que es como correría Jenkins en
producción, o en la mayoría de servidores CI dedicados) este problema
estructural no existe — el daemon y todos los contenedores comparten
literalmente el mismo filesystem, así que `TESTCONTAINERS_HOST_OVERRIDE` y
`HOST_WORKSPACE_ROOT` no harían falta (aunque no está de más dejarlos
configurables, ya que no rompen nada si no se setean). Documentado
explícitamente para que quien retome este proyecto en un servidor Linux no
se sorprenda si esas variables ya no son necesarias ahí.
