// CICD-002: pipeline declarativo con los 10 stages del ticket, en paridad con
// .github/workflows/ci.yml (CICD-001) — mismos comandos de Gradle/Docker/ZAP, misma
// adaptacion (docker-compose.staging.yml real, INFRA-004). Requiere el controlador
// Jenkins de jenkins/Dockerfile (JDK-21 + Docker CLI + python3 preinstalados,
// Configuration as Code en jenkins/jenkins-casc.yml) — ver docs/cicd/jenkins.md.
pipeline {
    agent any

    tools {
        jdk 'JDK-21'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        // GHCR exige minusculas; el nombre real del repo (Janner11/inventory-system)
        // tiene mayusculas (mismo problema resuelto en ci.yml, CICD-001).
        IMAGE_TAG      = "jenkins-${env.BUILD_NUMBER}"
        BACKEND_IMAGE  = "ghcr.io/janner11/inventory-system/backend:${IMAGE_TAG}"
        FRONTEND_IMAGE = "ghcr.io/janner11/inventory-system/frontend:${IMAGE_TAG}"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                sh 'chmod +x backend/gradlew scripts/*.sh'
                echo "Branch: ${env.BRANCH_NAME ?: env.GIT_BRANCH} — Build #${env.BUILD_NUMBER}"
            }
        }

        stage('Build Backend') {
            steps {
                dir('backend') {
                    sh './gradlew build -x test'
                }
            }
        }

        stage('Unit Tests') {
            steps {
                dir('backend') {
                    // jacocoTestCoverageVerification: quality gate real y activo hoy
                    // (85% lineas/65% branches en ProductService/StockService, TEST-001)
                    // - catchError marca la etapa FAILURE sin abortar el pipeline
                    // entero, para que "Publish Reports" (stage 10) siempre se alcance.
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh './gradlew test --tests "com.inventario.unit.*" jacocoTestReport jacocoTestCoverageVerification'
                    }
                }
            }
        }

        stage('Integration Tests') {
            // Incluye API Tests (com.inventario.api.*) — el Alcance Tecnico de este
            // ticket lista un solo stage "Integration Tests" (a diferencia de
            // ci.yml/CICD-001, que sí los separa en 2 jobs); se mantiene un solo stage
            // aca para respetar el nombre y la cantidad exactos que pide este ticket.
            // Ninguno de los dos depende de staging - usan Testcontainers propios
            // (SecurityIntegrationTest con KeycloakContainer real, TEST-002) o mocks
            // (AbstractApiTest con @MockBean JwtDecoder, TEST-003). Requiere Docker
            // accesible desde el agente de Jenkins (Testcontainers) - ver "Caso de
            // Error" del ticket y docs/cicd/jenkins.md.
            steps {
                dir('backend') {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh './gradlew test --tests "com.inventario.integration.*" --tests "com.inventario.api.*"'
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                sh "docker build -t ${BACKEND_IMAGE} backend/"
                sh """
                    docker build \
                      --build-arg VITE_API_BASE_URL=http://localhost:8082/api \
                      --build-arg VITE_KEYCLOAK_URL=http://localhost:8180 \
                      --build-arg VITE_KEYCLOAK_REALM=inventario \
                      --build-arg VITE_KEYCLOAK_CLIENT_ID=inventario-frontend \
                      -t ${FRONTEND_IMAGE} frontend/
                """
            }
        }

        stage('Deploy Staging') {
            // docker-compose.staging.yml (INFRA-004) real - mismo mecanismo que el job
            // "staging-e2e-security" de ci.yml: .env.staging generado desde el .example
            // con valores no-secretos validos solo para este workspace efimero de
            // Jenkins, apuntando BACKEND_IMAGE/FRONTEND_IMAGE a las imagenes recien
            // construidas en el stage anterior (mismo daemon Docker, sin necesitar push
            // a ningun registry).
            steps {
                sh '''
                    cp .env.staging.example .env.staging
                    sed -i "s#^BACKEND_IMAGE=.*#BACKEND_IMAGE=${BACKEND_IMAGE}#" .env.staging
                    sed -i "s#^FRONTEND_IMAGE=.*#FRONTEND_IMAGE=${FRONTEND_IMAGE}#" .env.staging
                    sed -i "s/CAMBIAR_password_staging_real/jenkins-staging-pg-pass/" .env.staging
                    sed -i "s/CAMBIAR_admin_password_staging_real/jenkins-staging-admin-pass/" .env.staging
                    sed -i "s/CAMBIAR_client_secret_staging_real/inventario-backend-secret/" .env.staging
                    sed -i "s/CAMBIAR_grafana_password_staging_real/jenkins-staging-grafana-pass/" .env.staging
                    # HOST_WORKSPACE_ROOT (docker-compose.dev.yml, servicio "jenkins"):
                    # en Docker Desktop, el daemon real solo resuelve bind mounts contra
                    # rutas reales de macOS, no contra rutas que solo existen DENTRO del
                    # contenedor de Jenkins - ver el comentario junto a esa variable. Sin
                    # setearla (host Linux real, sin esta capa de traduccion) el script cae
                    # a $(pwd), su comportamiento normal.
                    if [ -n "${HOST_WORKSPACE_ROOT:-}" ]; then
                      export COMPOSE_PROJECT_DIR="${HOST_WORKSPACE_ROOT}/${JOB_NAME}"
                      # Mismo motivo Docker Desktop: "localhost" dentro del contenedor de
                      # Jenkins no es el host real donde start-staging.sh verifica
                      # /actuator/health tras levantar el stack.
                      export HEALTH_CHECK_HOST=host.docker.internal
                    fi
                    ./scripts/start-staging.sh
                '''
            }
        }

        stage('E2E Tests') {
            // node:20 (no una imagen mcr.microsoft.com/playwright con version fija) +
            // "npx playwright install" instala los browsers que correspondan a la version
            // exacta de @playwright/test resuelta por npm ci (package-lock.json) - una
            // imagen Playwright pineada a una version vieja (probado real: v1.48.0-jammy)
            // rompe los 27 tests con "Executable doesn't exist" en cuanto el proyecto
            // sube de version (hoy en 1.61.1), sin ningun cambio de codigo de por medio.
            // --network host: el daemon Docker es el mismo host que publica los puertos de
            // staging (Docker-outside-of-Docker, ver jenkins/Dockerfile), así que
            // localhost:8090 dentro de este contenedor efímero sí alcanza el frontend de
            // staging. La fuente del bind mount usa HOST_WORKSPACE_ROOT en vez de
            // "$(pwd)" por el mismo motivo que en "Deploy Staging" arriba (Docker Desktop).
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        FRONTEND_DIR="$(pwd)/frontend"
                        if [ -n "${HOST_WORKSPACE_ROOT:-}" ]; then
                          FRONTEND_DIR="${HOST_WORKSPACE_ROOT}/${JOB_NAME}/frontend"
                        fi
                        # --shm-size: el /dev/shm por defecto de Docker (64MB) es
                        # insuficiente para Chromium y produce "Navigation failed because
                        # page crashed!" bajo carga (visto real corriendo junto al resto
                        # del stack de este proyecto en la misma maquina) - mitigacion
                        # estandar y documentada de Playwright/Chromium-en-Docker.
                        docker run --rm --network host --shm-size=1g \
                          -v "${FRONTEND_DIR}:/work" -w /work \
                          -e BASE_URL=http://localhost:8090 \
                          node:20 \
                          sh -c "npm ci && npx playwright install --with-deps chromium && npx playwright test --project=chromium"
                    '''
                }
            }
        }

        stage('Security Scan') {
            // ZAP baseline contra el frontend de staging ya desplegado - mismo mecanismo
            // que security-scan.yml (TEST-005) y el job staging-e2e-security de ci.yml
            // (CICD-001). El quality gate real (0 HIGH) corre en el stage siguiente. Mismo
            // motivo que arriba para HOST_WORKSPACE_ROOT.
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        mkdir -p zap-reports
                        cp zap-ignore-rules.conf zap-reports/zap-ignore-rules.conf
                        ZAP_REPORTS_DIR="$(pwd)/zap-reports"
                        if [ -n "${HOST_WORKSPACE_ROOT:-}" ]; then
                          ZAP_REPORTS_DIR="${HOST_WORKSPACE_ROOT}/${JOB_NAME}/zap-reports"
                        fi
                        docker run --network host --rm \
                          -v "${ZAP_REPORTS_DIR}:/zap/wrk/:rw" \
                          zaproxy/zap-stable zap-baseline.py \
                          -t http://localhost:8090/ \
                          -c zap-ignore-rules.conf \
                          -r baseline-report.html \
                          -x baseline-report.xml \
                          -J baseline-report.json \
                          -I
                    '''
                }
            }
        }

        stage('Quality Gate') {
            // 2 gates independientes: (1) ZAP - 0 vulnerabilidades HIGH (real, siempre
            // activo, TEST-005) y (2) SonarQube - "Inventario Quality Gate" (Coverage
            // >= 70%, 0 new bugs, 0 new vulnerabilities, <= 10 new code smells,
            // duplicacion <= 3%; ver docs/cicd/sonarqube.md). CICD-003 desplego el
            // servidor real (docker-compose.dev.yml, servicio "sonarqube") en la MISMA
            // red Docker que este contenedor de Jenkins - se alcanza por nombre de
            // servicio ("http://sonarqube:9000"), sin necesitar exponerlo al host. Si la
            // credencial "sonar-token" esta vacia (servidor no desplegado en este
            // entorno), se omite en vez de fallar - mismo criterio que el job
            // "sonarqube" de ci.yml, que ademas levanta su propio SonarQube efimero
            // cuando no hay uno persistente.
            steps {
                sh 'python3 scripts/zap-report-gate.py zap-reports/baseline-report.xml'
                script {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        if (env.SONAR_TOKEN?.trim()) {
                            // Encontrado real: el cache de scanner-engine en
                            // ~/.sonar/cache (workspace persistente entre builds, a
                            // diferencia de un runner efimero de GitHub Actions) puede
                            // quedar con una version incompatible con la API del
                            // servidor real ("Error 404 ... Unknown url:
                            // /api/ce/submit") si alguna corrida anterior en este mismo
                            // Jenkins bootstrapeo un engine distinto (ej. contra otra
                            // instancia/version de SonarQube). Limpiarlo antes de cada
                            // analisis evita esa clase de fallo por unos segundos extra
                            // de descarga.
                            sh 'rm -rf "$HOME/.sonar/cache"'
                            dir('backend') {
                                withEnv(["SONAR_HOST_URL=http://sonarqube:9000"]) {
                                    sh './gradlew sonar'
                                }
                            }
                        } else {
                            echo 'Quality Gate: SONAR_TOKEN vacio - sin credencial configurada para el SonarQube real (docker-compose.dev.yml), se omite el analisis.'
                        }
                    }
                }
            }
        }

        stage('Publish Reports') {
            // Publicacion explicita como stage propio (visible con su propio color en
            // Blue Ocean/Stage View, Validaciones del ticket) ademas del respaldo en
            // post{always{}} de abajo, que garantiza que los reportes se publiquen
            // incluso si un stage anterior abortara el pipeline por completo (los
            // catchError() de arriba ya evitan eso en el camino feliz, pero post{} sigue
            // siendo la red de seguridad real ante un fallo no capturado).
            steps {
                script {
                    publishReports()
                }
            }
        }
    }

    post {
        always {
            script {
                publishReports()
            }
            sh 'docker compose --env-file .env.staging -f docker-compose.staging.yml down -v || true'
        }
        success {
            echo "Pipeline completado exitosamente — Build #${env.BUILD_NUMBER}"
        }
        failure {
            echo "Pipeline fallido — revisar logs para diagnostico"
        }
        unstable {
            echo "Pipeline inestable — algunos tests/gates fallaron, ver stages en rojo/amarillo"
        }
    }
}

// Compartida entre el stage "Publish Reports" y post{always{}} — idempotente
// (allowEmptyResults/allowMissing en true), así que ejecutarla dos veces en el camino
// feliz no duplica nada, solo confirma que los reportes ya quedaron publicados.
def publishReports() {
    junit allowEmptyResults: true,
          testResults: 'backend/build/test-results/test/*.xml'

    publishHTML(target: [
        allowMissing         : true,
        alwaysLinkToLastBuild: true,
        keepAll              : true,
        reportDir            : 'backend/build/reports/jacoco/test/html',
        reportFiles          : 'index.html',
        reportName           : 'Cobertura JaCoCo'
    ])

    publishHTML(target: [
        allowMissing         : true,
        alwaysLinkToLastBuild: true,
        keepAll              : true,
        reportDir            : 'backend/build/reports/tests/test',
        reportFiles          : 'index.html',
        reportName           : 'Reporte de Tests (JUnit/RestAssured)'
    ])

    publishHTML(target: [
        allowMissing         : true,
        alwaysLinkToLastBuild: true,
        keepAll              : true,
        reportDir            : 'frontend/playwright-report',
        reportFiles          : 'index.html',
        reportName           : 'Reporte E2E (Playwright)'
    ])

    publishHTML(target: [
        allowMissing         : true,
        alwaysLinkToLastBuild: true,
        keepAll              : true,
        reportDir            : 'zap-reports',
        reportFiles          : 'baseline-report.html',
        reportName           : 'Reporte de Seguridad (OWASP ZAP)'
    ])

    archiveArtifacts artifacts: 'backend/build/libs/*.jar, zap-reports/*.xml',
                     allowEmptyArchive: true
}
