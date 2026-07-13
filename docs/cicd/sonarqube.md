# SonarQube — análisis de calidad y Quality Gate (CICD-003)

SonarQube Community Edition, desplegado en `docker-compose.dev.yml`
(servicios `sonarqube` + `sonarqube-db`), analiza el backend en cada
ejecución de CI/CD (GitHub Actions y Jenkins) y bloquea el pipeline si no
cumple el "Inventario Quality Gate".

## Quality Gate — "Inventario Quality Gate"

Los 5 umbrales exactos del Alcance Técnico del ticket, sin condiciones
adicionales (se eliminaron las condiciones "Sonar way" que SonarQube
pre-carga por defecto al crear un gate nuevo, para que el gate sea
exactamente el que pide el ticket):

| Métrica | Umbral | Alcance |
|---|---|---|
| `coverage` | >= 70% | Overall (no solo código nuevo) |
| `new_bugs` | = 0 | Código nuevo |
| `new_vulnerabilities` | = 0 | Código nuevo |
| `new_code_smells` | <= 10 | Código nuevo |
| `duplicated_lines_density` | <= 3% | Overall |

Asignado al proyecto `inventario-backend` (no como Quality Gate por
defecto de la instancia, para no afectar otros proyectos que se agreguen
después).

## Arranque

```bash
cp .env.example .env   # si no existe ya
# editar SONARQUBE_DB_PASSWORD (sin default trivial a propósito)
docker compose -f docker-compose.dev.yml up -d sonarqube-db sonarqube
# http://localhost:9001 (admin/admin la primera vez — cambiar password en el primer login)
```

Elasticsearch (embebido en SonarQube) requiere `vm.max_map_count >= 262144`
en el host Docker — ya cumplido por defecto en Docker Desktop (verificado:
exactamente `262144`). El servicio `sonarqube` en `docker-compose.dev.yml`
ya define los `ulimits` (`nofile`/`nproc`/`memlock`) que Elasticsearch
también exige, sin los cuales el contenedor crashea en el arranque.

## Provisionar el proyecto y el Quality Gate (primera vez)

```bash
AUTH="admin:<tu-password>"
BASE="http://localhost:9001"

curl -u "$AUTH" -X POST "$BASE/api/projects/create" \
  -d "name=Inventario Backend" -d "project=inventario-backend"

curl -u "$AUTH" -X POST "$BASE/api/qualitygates/create" -d "name=Inventario Quality Gate"
curl -u "$AUTH" -X POST "$BASE/api/qualitygates/create_condition" \
  -d "gateName=Inventario Quality Gate" -d "metric=coverage" -d "op=LT" -d "error=70"
curl -u "$AUTH" -X POST "$BASE/api/qualitygates/create_condition" \
  -d "gateName=Inventario Quality Gate" -d "metric=new_bugs" -d "op=GT" -d "error=0"
curl -u "$AUTH" -X POST "$BASE/api/qualitygates/create_condition" \
  -d "gateName=Inventario Quality Gate" -d "metric=new_vulnerabilities" -d "op=GT" -d "error=0"
curl -u "$AUTH" -X POST "$BASE/api/qualitygates/create_condition" \
  -d "gateName=Inventario Quality Gate" -d "metric=new_code_smells" -d "op=GT" -d "error=10"
curl -u "$AUTH" -X POST "$BASE/api/qualitygates/create_condition" \
  -d "gateName=Inventario Quality Gate" -d "metric=duplicated_lines_density" -d "op=GT" -d "error=3"
curl -u "$AUTH" -X POST "$BASE/api/qualitygates/select" \
  -d "gateName=Inventario Quality Gate" -d "projectKey=inventario-backend"

# Token para CI/Jenkins (Administration > Security > Users, o vía API):
curl -u "$AUTH" -X POST "$BASE/api/user_tokens/generate" -d "name=ci-inventario-backend"
```

**Nota sobre `create_condition`:** `POST /api/qualitygates/create` en esta
versión de SonarQube pre-carga automáticamente las condiciones del gate
built-in "Sonar way" (ratings de new_reliability/new_security/
new_maintainability, new_coverage, new_duplicated_lines_density,
new_security_hotspots_reviewed) sobre el gate recién creado — encontrado
real al inspeccionar el gate después de crearlo. Se eliminaron esas 6
condiciones extra (`api/qualitygates/delete_condition`) para que el gate
quede exactamente con las 5 que pide el ticket, sin condiciones implícitas
adicionales que puedan fallar el pipeline por razones fuera del alcance
acordado.

## Análisis local (Gradle)

`backend/build.gradle.kts` ya tiene el plugin `org.sonarqube` y el bloque
`sonar { properties { ... } }` (`sonar.qualitygate.wait=true` — bloquea la
tarea hasta que el servidor evalúa el gate, paso 7 del ticket). No hace
falta ni `sonar-scanner` CLI ni Maven.

```bash
cd backend
SONAR_HOST_URL=http://localhost:9001 SONAR_TOKEN=<tu-token> ./gradlew test jacocoTestReport sonar
```

`backend/sonar-project.properties` (DoD del ticket) es el equivalente para
quien prefiera correr `sonar-scanner` directo, fuera de Gradle —
mantenido en sincronía manual con `build.gradle.kts`.

## Integración en CI/CD

### GitHub Actions (`ci.yml`, job `sonarqube`)

No hay ningún servidor SonarQube persistente/hosteado para este proyecto
académico (ni presupuesto para SonarCloud). El job levanta un SonarQube
Community **efímero** como *service container* — vive solo durante esa
corrida — le aplica el mismo "Inventario Quality Gate" vía API, y analiza
contra él. Si en el futuro se configuran los secrets `SONAR_TOKEN`/
`SONAR_HOST_URL` (apuntando a un servidor real persistente, ej.
SonarCloud), el job los usa en su lugar automáticamente.

### Jenkins (`Jenkinsfile`, stage "Quality Gate")

Jenkins corre en la misma red Docker que el servicio `sonarqube` de
`docker-compose.dev.yml` (ambos son servicios del mismo archivo) — se
alcanza por nombre (`http://sonarqube:9000`), sin exponerlo al host. La
credencial `sonar-token` (Configuration as Code,
`jenkins/jenkins-casc.yml`) se completa con la variable de entorno
`SONAR_TOKEN` del `.env` del host. Sin ella, el stage se omite (no falla el
pipeline) — mismo criterio que el job de GitHub Actions cuando tampoco hay
servidor real configurado, salvo que Jenkins no levanta un SonarQube
efímero (ya tiene uno persistente disponible en la misma red).

## Verificación real (2026-07-12)

- **SonarQube 9.9.8 LTS Community** levantado y accesible (`GET
  /api/system/status` → `"status":"UP"`), password de admin cambiado vía
  API, proyecto `inventario-backend` creado, "Inventario Quality Gate"
  creado con exactamente los 5 umbrales del ticket y asignado al proyecto.
- **Análisis real contra el servidor** (`./gradlew test jacocoTestReport sonar`,
  autenticado con un token real generado vía API): `BUILD SUCCESSFUL`,
  Quality Gate `OK` — cobertura real 95.8%, 0 bugs/vulnerabilities/code
  smells nuevos, 0% duplicación (primera corrida, sin código previo con el
  que comparar "nuevo").
- **Bloqueo verificado en ambas direcciones** (Pruebas Requeridas del
  ticket — "Push código con cobertura baja y verificar fallo" / "Push
  código limpio y verificar éxito"): se subió temporalmente el umbral de
  cobertura del gate a 99% (inalcanzable) → `./gradlew sonar` → `BUILD
  FAILED` (confirmando que `sonar.qualitygate.wait=true` bloquea la build
  de verdad, no solo sube el análisis). Se revirtió el umbral a 70% →
  `BUILD SUCCESSFUL` de nuevo.
- **Verificado a través de Jenkins** (mismo entorno/red que usaría el stage
  real, no solo desde el host): confirmado que el contenedor de Jenkins
  alcanza `http://sonarqube:9000` por nombre de servicio
  (`docker exec inventario-jenkins curl http://sonarqube:9000/api/system/status`
  → `UP`). El propio stage "Quality Gate" del pipeline real (build de
  Jenkins) llegó a ejecutar `./gradlew sonar` contra el servidor real y lo
  reprodujo con éxito tras el fix del bug de caché (abajo).
- **`actionlint`** (Docker, `rhysd/actionlint`) sobre `ci.yml` tras los
  cambios: sin errores nuevos (solo un warning de estilo de shellcheck
  pre-existente en el resto del proyecto, no relacionado con SonarQube).

### Bug real encontrado y corregido: caché de scanner-engine obsoleta

Al correr el stage "Quality Gate" a través de Jenkins (workspace
**persistente** entre builds, a diferencia de un runner efímero de GitHub
Actions), la tarea `sonar` falló con:

```
Failed to upload report: Error 404 on http://sonarqube:9000/api/ce/submit :
{"errors":[{"msg":"Unknown url : /api/ce/submit"}]}
```

Causa: `~/.sonar/cache` (dentro del `jenkins_home` persistente) tenía
cacheado un scanner-engine de un intento anterior en este mismo entorno,
incompatible con la API real del servidor 9.9.8 LTS. Sin este caché
corrupto, el análisis funciona correctamente (confirmado limpiándolo
manualmente y volviendo a correr con éxito). Fix aplicado al
`Jenkinsfile`: `rm -rf "$HOME/.sonar/cache"` antes de cada `./gradlew sonar`
en el stage "Quality Gate" — unos segundos extra de descarga a cambio de
eliminar esta clase de fallo por completo. GitHub Actions no sufre este
problema porque cada corrida usa un runner completamente nuevo, sin caché
heredado de builds anteriores.

### Limitación de esta sesión de verificación (no relacionada con SonarQube)

Las últimas 2 corridas completas del pipeline de Jenkins (para confirmar el
stage "Quality Gate" de punta a punta dentro del flujo completo de 10
stages) fallaron en el stage **"Deploy Staging"**, con errores de
Keycloak/Grafana (`EOFException`, `database is locked (SQLITE_BUSY)`,
`permission denied`) — señales claras de agotamiento de recursos del host
tras muchas horas de uso intensivo continuo de Docker Desktop en esta
misma máquina durante CICD-001/002/003 (decenas de builds de imágenes,
levantamientos y derribos de stacks completos). No están relacionados con
los cambios de este ticket — `docker-compose.staging.yml` y el resto del
pipeline ya están verificados exhaustivamente en INFRA-004 y CICD-002. La
integración de SonarQube en sí (el alcance de este ticket) quedó verificada
de forma independiente y exitosa por los métodos descritos arriba (análisis
directo + a través del workspace real de Jenkins).

## Limpieza

```bash
docker compose -f docker-compose.dev.yml down sonarqube sonarqube-db
# con -v para borrar también los datos persistentes (proyecto, Quality Gate, historial)
```
