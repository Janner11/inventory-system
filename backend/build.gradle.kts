plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.owasp.dependencycheck") version "12.2.2"
    // CICD-001: plugin agregado para que el job "sonarqube" de ci.yml pueda correr
    // `./gradlew sonar` de verdad. No hay ningun servidor SonarQube desplegado en este
    // proyecto todavia (ni local ni remoto) - provisionar uno, definir el Quality Gate y
    // hacer que bloquee el pipeline es el alcance completo de CICD-003 (ticket separado,
    // "SonarQube integracion y quality gates", 5 SP). Sin `sonar.host.url`/`SONAR_TOKEN`
    // configurados, el job de CI detecta la ausencia del secret y omite este paso en vez
    // de fallar - el plugin en si es inerte hasta que se invoca la tarea `sonar`.
    id("org.sonarqube") version "5.1.0.4882"
}

group = "com.inventario"
version = "0.1.0-SNAPSHOT"
description = "Sistema de Gestion de Inventarios Empresarial - Backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val mapstructVersion = "1.5.5.Final"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    implementation("org.hibernate.orm:hibernate-envers")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // TEST-002: Keycloak real en tests de integracion (SecurityIntegrationTest), en vez
    // de mockear JwtDecoder como hacen los tests unitarios/api.
    testImplementation("com.github.dasniko:testcontainers-keycloak:3.5.1")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.rest-assured:json-schema-validator")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Docker Desktop exige API >= 1.40; el docker-java shaded de Testcontainers
    // usa esta system property (no DOCKER_API_VERSION) para fijar la version
    // de la API usada al hablar con el daemon.
    systemProperty("api.version", "1.41")

    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// TEST-002: copia keycloak/realm.json (fuente unica de verdad, ADR-008, ya usado por
// docker-compose.dev.yml) al classpath de test en vez de duplicarlo a mano en
// src/test/resources - asi SecurityIntegrationTest nunca puede quedar desincronizada
// del realm real.
val copyKeycloakRealmForTests by tasks.registering(Copy::class) {
    from(rootProject.projectDir.parentFile.resolve("keycloak/realm.json"))
    into(layout.buildDirectory.dir("resources/test/keycloak"))
}

tasks.processTestResources {
    dependsOn(copyKeycloakRealmForTests)
}

// TEST-005: OWASP Dependency-Check (seccion 11 de CLAUDE.md ya lo listaba, sin implementar).
// El ticket describia "pom.xml" (Maven) - este proyecto usa Gradle (sin pom.xml, sin Maven
// en ningun lado del repo), mismo criterio de adaptacion ya usado en el resto del backlog
// (TEST-003 "maven-failsafe-plugin" -> tareas de Gradle equivalentes).
dependencyCheck {
    // "Dependency Check sin vulnerabilidades CRITICAL" (validaciones del ticket) - CRITICAL
    // en la escala CVSSv3 del NVD es >= 9.0.
    failBuildOnCVSS = 9.0f
    formats = listOf("HTML", "XML", "JSON")
    setOutputDirectory(layout.buildDirectory.dir("reports/dependency-check").get().asFile)
    suppressionFiles = listOf(rootProject.projectDir.resolve("dependency-check-suppressions.xml").path)

    // Ruta fija dentro de build/ (en vez del default en el home del usuario) para poder
    // cachearla entre corridas de CI (actions/cache, ver security-scan.yml).
    data.directory = layout.buildDirectory.dir("dependency-check-data").get().asFile.absolutePath

    // Sin NVD_API_KEY las actualizaciones de la base de datos del NVD son extremadamente
    // lentas (rate limit publico) - ver .env.example / seccion 15 de CLAUDE.md. Con la
    // variable seteada, se usa; sin ella, dependency-check sigue funcionando (mas lento).
    System.getenv("NVD_API_KEY")?.let { nvd.apiKey = it }

    // Solo las dependencias que realmente terminan en el artefacto desplegado - las de
    // solo-test (Testcontainers, RestAssured, JUnit, etc.) no representan riesgo en
    // produccion y solo agregan ruido/tiempo de escaneo.
    scanConfigurations = listOf("runtimeClasspath")

    analyzers {
        // Analizadores para ecosistemas que este proyecto no usa (Node/.NET/Python/Ruby) -
        // deshabilitarlos evita falsos positivos y acelera el analisis.
        assemblyEnabled = false
        nodeEnabled = false
        nuspecEnabled = false
        nugetconfEnabled = false
    }
}

// CICD-001: configuracion minima del proyecto para el analisis Sonar (host/token se
// pasan por linea de comandos en ci.yml via -Dsonar.host.url/-Dsonar.token, no
// hardcodeados aqui - ninguno de los dos existe todavia, ver nota del plugin arriba).
sonar {
    properties {
        property("sonar.projectKey", "inventario-backend")
        property("sonar.projectName", "Inventario Backend")
        property("sonar.sources", "src/main/java")
        property("sonar.tests", "src/test/java")
        property("sonar.java.binaries", layout.buildDirectory.dir("classes/java/main").get().asFile.path)
        property("sonar.coverage.jacoco.xmlReportPaths", layout.buildDirectory.dir("reports/jacoco/test/jacocoTestReport.xml").get().asFile.path)
    }
}
