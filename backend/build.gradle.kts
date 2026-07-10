plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
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
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testImplementation("io.rest-assured:rest-assured")
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

// TEST-001: JaCoCo "obligatorio" -> el build falla si la cobertura de ProductService/
// StockService (lo que este ticket prueba explicitamente) cae por debajo del >85%
// linea que pedia su propio alcance completo. Acotado por clase a esas 2 clases
// exactas (no a todo com.inventario.service.*): AuditService hoy tiene 0% de
// cobertura unitaria (solo lo cubre AuditServiceIntegrationTest, @DataJpaTest, fuera
// del alcance de TEST-001) y arrastraria el gate a un falso negativo si se incluyera.
// Tampoco es un gate a nivel de todo el bundle: el job "Unit Tests" de CI/Jenkins
// corre solo `--tests "com.inventario.unit.*"`, y con ese filtro la cobertura GLOBAL
// del proyecto cae a ~67% (repositorios/mappers/config solo se ejercitan con los
// tests de integracion de TEST-002, en otro stage) - un gate global aqui tambien
// seria un falso negativo. ProductService/StockService llegan a 100% linea solo con
// los tests de este ticket, sin depender de que otro stage haya corrido antes.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)

    violationRules {
        rule {
            element = "CLASS"
            includes = listOf(
                "com.inventario.service.ProductService",
                "com.inventario.service.StockService",
            )

            limit {
                counter = "LINE"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.65".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
