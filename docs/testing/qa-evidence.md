# Evidencia de QA

Consolidado de la evidencia real de calidad del proyecto — resultados de
ejecuciones reales, no cifras objetivo sin verificar. Cada sección enlaza
al documento fuente con el detalle completo/metodología; este documento es
el resumen ejecutivo para revisión rápida.

## Resumen ejecutivo

| Dimensión | Resultado | Fuente |
|---|---|---|
| Tests automatizados (backend) | **248/248 pasando** (136 unit + 58 integration + 54 API) | [`testing-strategy.md`](testing-strategy.md) |
| Tests automatizados (frontend) | **62/62 pasando** (unit, Vitest) | [`testing-strategy.md`](testing-strategy.md) |
| Tests E2E | **54/54 pasando** (7 specs × Chromium + Firefox) | [`testing-strategy.md`](testing-strategy.md) |
| Cobertura de código | **100% líneas** en `ProductService`/`StockService`; gate real ≥85%/65% | Ver [Cobertura](#cobertura-de-código) abajo |
| Quality Gate (SonarQube) | ✅ **OK** — 0 bugs/vulnerabilidades nuevos, cobertura real 95.8% | [`../cicd/sonarqube.md`](../cicd/sonarqube.md) |
| Seguridad dinámica (OWASP ZAP) | **0 alertas HIGH** (frontend y backend) | Ver [Seguridad](#seguridad) abajo |
| Dependencias (OWASP Dependency-Check) | **0 vulnerabilidades CVSS ≥ 9.0** | Ver [Seguridad](#seguridad) abajo |
| Imágenes Docker (Trivy) | **0 CRITICAL sin justificar** (1 aceptado y documentado) | Ver [Seguridad](#seguridad) abajo |
| Rendimiento (k6, carga normal) | p95 **19.55ms**, error **0.40%**, throughput **128.98 req/s** | [`../performance.md`](../performance.md) |
| Exploratory testing | 3 sesiones, 5 bugs documentados, 14 hallazgos confirmados como seguros | [`exploratory-testing-report.md`](exploratory-testing-report.md) |

## Cobertura de código

`./gradlew test jacocoTestReport jacocoTestCoverageVerification`:

- `ProductService`: **100% líneas / 86.4% branches**
- `StockService`: **100% líneas / 100% branches**
- Gate real (`jacocoTestCoverageVerification`) acotado a esas 2 clases —
  bloquea el build si bajan de 85% líneas / 65% branches. Corre en cada PR
  (`ci.yml`, job `unit-tests`) y en Jenkins (stage "Unit Tests").
- Cobertura global del proyecto (todas las clases, todos los niveles de
  test combinados): **95.8%**, según el último análisis real de SonarQube
  (ver abajo) — por encima del objetivo general de ≥70% de la sección de
  Testing del backlog.

## Quality Gate — SonarQube

"Inventario Quality Gate" (5 condiciones exactas: coverage ≥70%, 0 new
bugs, 0 new vulnerabilities, ≤10 new code smells, duplicación ≤3%):

```
BUILD SUCCESSFUL — Quality Gate: OK
Coverage: 95.8%
New bugs: 0 | New vulnerabilities: 0 | New code smells: 0 | Duplicated lines: 0%
```

Bloqueo verificado en ambas direcciones (umbral inalcanzable → `BUILD
FAILED`; revertido → `BUILD SUCCESSFUL`). Detalle completo, incluyendo cómo
se provisionó el servidor y el bug real encontrado (caché de
scanner-engine obsoleta en Jenkins) en [`../cicd/sonarqube.md`](../cicd/sonarqube.md).

## Seguridad

### OWASP ZAP (dinámico)

| Scan | Alertas HIGH | Alertas Medium/Low/Info | Fuente |
|---|---|---|---|
| Baseline (frontend) | **0** | 4 Medium / 4 Low / 2 Info, todas documentadas con causa raíz verificada en `zap-ignore-rules.conf` | Reporte real, última corrida de pipeline Jenkins (`jenkins-workspace/inventario-pipeline/zap-reports/baseline-report.json`) |
| API scan (backend, `/v3/api-docs`) | **0** | Documentadas en `zap-ignore-rules.conf` | `security-scan.yml` |

Ninguna alerta Medium/Low se suprime sin verificación manual — cada una en
`zap-ignore-rules.conf` tiene un comentario explicando por qué es un falso
positivo o un riesgo aceptado (ej. timestamps que en realidad son
constantes criptográficas de una librería, o la ausencia de TLS en un
entorno de desarrollo local sin dominio público).

### OWASP Dependency-Check (dependencias de aplicación)

```
./gradlew dependencyCheckAnalyze
0 vulnerabilidades con CVSS >= 9.0 (CRITICAL) en runtimeClasspath
```

### Trivy (imágenes Docker)

| Imagen | CRITICAL sin justificar | Nota |
|---|---|---|
| `inventario-backend` | **0** | 1 CRITICAL real (`CVE-2026-22732`, Spring Security) documentado como riesgo aceptado en `backend/.trivyignore` — sin fix disponible en la línea de Spring Boot actual del proyecto, ver `docs/architecture.md` |
| `inventario-frontend` | **0** | Sin hallazgos |

Gate real (`--severity CRITICAL --exit-code 1`) activo en `ci.yml` y
`Jenkinsfile`, no solo verificación manual.

## Rendimiento

Resumen de las 3 corridas reales (metodología completa, gráficas de
degradación y análisis de causa raíz en [`../performance.md`](../performance.md)):

| Escenario | Resultado |
|---|---|
| Load test (50 VU, 5 min) | p95 **19.55ms** (25× margen sobre el objetivo de 500ms), error **0.40%**, **128.98 req/s** — los 3 thresholds pasan |
| Stress test (ramping a 200 VU) | Punto de quiebre real identificado (~100-150 VU), causa raíz: saturación del pool de conexiones HikariCP, no la JVM — sin caídas del servicio |
| Soak test (50 VU, 30 min) | Sin evidencia de fuga de memoria (tasa de GC estable en los últimos 20 min) |

## Exploratory testing

3 sesiones con formato SBTM (~150 min totales) — auth/autorización,
formularios/validaciones, flujos de stock. Resultado: **5 bugs reales
documentados** (3 Medium de configuración de Keycloak/Actuator, 1 Medium de
validación de datos faltante, 1 Low de UX) y **14 hallazgos confirmados
como seguros** (incluyendo una verificación real de que 5 requests
concurrentes de salida de stock nunca sobrevenden el inventario). Detalle
completo por charter en [`exploratory-testing-report.md`](exploratory-testing-report.md).

## Trazabilidad de requisitos

Cada requisito funcional y no funcional de [`../requirements.md`](../requirements.md)
enlaza a su implementación real (endpoint/clase/archivo de configuración) y
a su verificación (test automatizado o caso manual de
[`test-cases.md`](test-cases.md)). No hay requisitos documentados sin una
forma de verificarlos.

## Cómo se generó este documento

Las cifras de este resumen se tomaron de: el último reporte ZAP real
generado por una corrida del pipeline Jenkins (`zap-reports/baseline-report.json`,
analizado programáticamente — 0 alertas `riskcode=3`/HIGH confirmadas), los
resultados de test ya documentados y verificados en sesiones anteriores
(`CLAUDE.md`, `docs/performance.md`, `docs/cicd/sonarqube.md`), y el conteo
real de test cases ejecutado al escribir [`testing-strategy.md`](testing-strategy.md).
No se inventó ninguna cifra — donde no había una verificación reciente
disponible, se referencia la última real documentada en vez de asumir un
número.
