# ===== Etapa 1: Build =====
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ===== Etapa 2: JRE a medida (jlink) =====
# eclipse-temurin:21-jre-alpine paso a pesar ~200MB (agrego fontconfig/ttf-dejavu/gnupg/
# musl-locales/tzdata como dependencias del propio JRE de Alpine, ninguna de las cuales
# usa este backend), lo que por si solo ya superaba el objetivo de <300MB del ticket.
# jlink construye un runtime minimo con solo los modulos que el jar realmente usa
# (jdeps los detecta automaticamente, incluyendo BOOT-INF/lib - no es una lista curada a
# mano, que se desincronizaria en cuanto cambien las dependencias del proyecto).
FROM eclipse-temurin:21-jdk-alpine AS jlink
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# "layertools" (no el mas nuevo "tools") a proposito: estable desde Spring Boot 2.3, sin
# depender de que la version exacta del plugin (3.3.5) soporte el modo mas nuevo. Extrae
# en ./dependencies/, ./spring-boot-loader/, ./snapshot-dependencies/, ./application/.
RUN java -Djarmode=layertools -jar app.jar extract
# "--class-path" (no jars posicionales): pasar los jars de dependencias como argumentos
# posicionales hace que jdeps los trate como "modulos iniciales" y fuerce la resolucion
# JPMS completa via module-info real (no Automatic-Module-Name) - jakarta.transaction-api
# declara "requires jakarta.interceptor"/"requires jakarta.cdi" (dependencias opcionales
# de compilacion de la especificacion, nunca usadas en runtime por Hibernate/Spring Data
# JPA aqui) que no existen como jar en este classpath, rompiendo la resolucion con
# "FindException: Module ... not found" (encontrado real con dos variantes de este error
# al intentar --recursive y luego sin --recursive, ambos como argumentos posicionales).
# Con "--class-path", jdeps analiza el bytecode real (que paquetes java.*/jdk.* se
# referencian de verdad) en vez de resolver el grafo declarado de modulos de terceros -
# es el patron estandar para calcular modulos de jlink sobre un fat jar de Spring Boot.
RUN jdeps \
      --ignore-missing-deps \
      --multi-release 21 \
      --print-module-deps \
      --class-path 'dependencies/BOOT-INF/lib/*' \
      application/BOOT-INF/classes \
      > /app/modules.txt
RUN jlink \
      --add-modules "$(cat /app/modules.txt)" \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --compress=zip-6 \
      --output /javaruntime

# ===== Etapa 3: Runtime =====
# alpine "a secas" (sin JDK/JRE de ningun tipo) - el unico Java en la imagen final es el
# runtime armado a medida por jlink en la etapa anterior. "3.22" (no "3.20"): Trivy senalo
# "This OS version is no longer supported by the distribution" para 3.20 (linea EOL, sin
# parches de seguridad futuros) al escanear la imagen (CICD-004) - 3.22 es una linea
# estable vigente.
FROM alpine:3.22
WORKDIR /app

ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jlink /javaruntime $JAVA_HOME

# OBS-001: OpenTelemetry Java Agent (instrumentacion automatica, ADR-006) — version
# pineada por reproducibilidad, mismo criterio que Loki/Tempo/Alertmanager (INFRA-003).
# v2.10.0 (fijada en OBS-001) tenia un CVE CRITICAL real (CVE-2026-33701, encontrado por
# Trivy en CICD-004) con fix en 2.26.1+ - actualizada a la ultima release estable (v2.29.0)
# al cerrar ese hallazgo.
ARG OTEL_AGENT_VERSION=v2.29.0
RUN apk add --no-cache wget \
  && wget -q -O otel-javaagent.jar \
     "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" \
  && apk del wget

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/build/libs/*.jar app.jar
USER spring:spring

EXPOSE 8081
# "/dev/tcp/..." (bash-ismo) no funciona en Alpine's ash - "can't create ... nonexistent
# directory", encontrado real al probar el contenedor. busybox (base de alpine:3.20, sin
# instalar nada extra) SI trae su propio applet "wget" - verificado con
# "docker exec ... which wget" -> /usr/bin/wget - asi que no hace falta el workaround de
# /dev/tcp ni dejar el "apk add wget" de la descarga del agente de OTel instalado.
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8081/actuator/health || exit 1

ENV JAVA_TOOL_OPTIONS="-javaagent:/app/otel-javaagent.jar"

ENTRYPOINT ["java", "-jar", "app.jar"]
