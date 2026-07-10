# ===== Etapa 1: Build =====
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ===== Etapa 2: Runtime =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# OBS-001: OpenTelemetry Java Agent (instrumentacion automatica, ADR-006) — version
# pineada por reproducibilidad, mismo criterio que Loki/Tempo/Alertmanager (INFRA-003).
ARG OTEL_AGENT_VERSION=v2.10.0
RUN wget -q -O otel-javaagent.jar \
  "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/build/libs/*.jar app.jar
USER spring:spring

EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8081/actuator/health || exit 1

ENV JAVA_TOOL_OPTIONS="-javaagent:/app/otel-javaagent.jar"

ENTRYPOINT ["java", "-jar", "app.jar"]
