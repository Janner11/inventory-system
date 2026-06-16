pipeline {
    agent any

    // Requires JDK 21 configured in Jenkins → Manage Jenkins → Tools → JDK installations → Name: "JDK-21"
    tools {
        jdk 'JDK-21'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                sh 'chmod +x backend/gradlew'
                echo "Branch: ${env.BRANCH_NAME ?: env.GIT_BRANCH} — Build #${env.BUILD_NUMBER}"
            }
        }

        stage('Build') {
            steps {
                dir('backend') {
                    sh './gradlew build -x test'
                }
            }
        }

        stage('Unit Tests') {
            steps {
                dir('backend') {
                    sh './gradlew test --tests "com.inventario.unit.*" jacocoTestReport'
                }
            }
        }

        stage('Integration & API Tests') {
            // Requires Docker daemon accessible from the Jenkins agent (used by Testcontainers)
            steps {
                dir('backend') {
                    sh './gradlew test --tests "com.inventario.integration.*" --tests "com.inventario.api.*"'
                }
            }
        }

        stage('Build Docker Image') {
            // Requires Docker daemon accessible from the Jenkins agent
            steps {
                dir('backend') {
                    sh 'docker build -t inventario-backend:${BUILD_NUMBER} -t inventario-backend:latest .'
                }
            }
        }

    }

    post {
        always {
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

            archiveArtifacts artifacts: 'backend/build/libs/*.jar',
                             allowEmptyArchive: true
        }
        success {
            echo "Pipeline completado exitosamente — Build #${env.BUILD_NUMBER}"
        }
        failure {
            echo "Pipeline fallido — revisar logs para diagnostico"
        }
        unstable {
            echo "Pipeline inestable — algunos tests fallaron"
        }
    }
}
