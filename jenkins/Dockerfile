# CICD-002: imagen custom del controlador Jenkins con plugins preinstalados (Alcance
# Tecnico del ticket: "docker-compose incluye jenkins service con plugins
# preinstalados") + Docker CLI (el Jenkinsfile hace "docker build"/"docker compose"
# directo con "sh", Docker-outside-of-Docker via el socket montado, ver
# docker-compose.dev.yml) + JDK 21 fijo (el mismo Jenkinsfile referencia
# "tools { jdk 'JDK-21' }" — se instala directo en la imagen en vez de depender del
# instalador automatico de JDKs de Jenkins, que requiere salida a internet en cada
# arranque de agente; mismo criterio de versiones pineadas por reproducibilidad ya usado
# en el resto del proyecto, INFRA-003/CICD-004).
FROM jenkins/jenkins:lts-jdk17

USER root

# Docker CLI + plugin docker compose v2 (el daemon real vive en el host, vía el socket
# montado — no se instala docker-ce completo, solo el cliente).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg gosu python3 \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
    && chmod a+r /etc/apt/keyrings/docker.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian bookworm stable" \
       > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin \
    && rm -rf /var/lib/apt/lists/*

# JDK 21 para el propio Jenkinsfile (gradlew de backend/ requiere Java 21) — el core de
# Jenkins sigue en JDK 17 (imagen base "lts-jdk17"), son JVMs independientes.
RUN apt-get update \
    && curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb bookworm main" \
       > /etc/apt/sources.list.d/adoptium.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends temurin-21-jdk \
    && rm -rf /var/lib/apt/lists/* \
    && ln -s /usr/lib/jvm/temurin-21-jdk-* /opt/jdk-21
# Symlink de nombre fijo (independiente de arquitectura/build exacto del paquete
# temurin-21-jdk) - referenciado como "home:" en jenkins-casc.yml sin necesitar
# interpolacion de variables de entorno dentro del YAML de JCasC.
ENV JDK21_HOME=/opt/jdk-21

# El usuario "jenkins" necesita pertenecer al grupo dueno de /var/run/docker.sock del
# HOST para poder ejecutar "docker"/"docker compose" sin sudo (Caso de Error del ticket:
# "Permisos de Docker en Jenkins - jenkins user en docker group"). El GID real del socket
# del host varia por maquina (en Docker Desktop/macOS suele ser distinto al "docker" GID
# de Debian dentro del contenedor) - se resuelve en runtime, no en build time, via
# entrypoint.sh (mismo GID en cada arranque del contenedor, no en la imagen).
COPY entrypoint.sh /usr/local/bin/jenkins-entrypoint.sh
RUN chmod +x /usr/local/bin/jenkins-entrypoint.sh

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# Configuration as Code (JCasC) - plugins, herramientas (JDK-21) y configuracion general
# aplicados automaticamente al arrancar, sin pasos manuales por el asistente de setup.
# Fuera de /var/jenkins_home a proposito: ese directorio se monta como volumen
# persistente en docker-compose.dev.yml (para no perder el estado de Jenkins entre
# reinicios), y un volumen vacio en el primer "up" taparia cualquier archivo horneado
# ahi por la imagen.
COPY jenkins-casc.yml /usr/local/jenkins-casc/jenkins-casc.yml
ENV CASC_JENKINS_CONFIG=/usr/local/jenkins-casc/jenkins-casc.yml
# Salta el wizard de "Unlock Jenkins"/plugins sugeridos - ya vienen preinstalados
# (Alcance Tecnico del ticket) y el resto de la config la aplica JCasC.
# ALLOW_LOCAL_CHECKOUT=true: el plugin Git de Jenkins bloquea por defecto cualquier
# remoto "file://" (lo trata como potencialmente inseguro) - necesario porque el job
# provisionado por JCasC (jenkins-casc.yml) usa "file:///workspace-repo" en vez de un
# webhook real de GitHub (ver "Casos de Error" del ticket y docs/cicd/jenkins.md).
# Encontrado real: sin esto, el build #1 de verificacion falló en el primer paso
# (Checkout) con "Checkout of Git remote... aborted because it references a local
# directory, which may be insecure".
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true"

ENTRYPOINT ["/usr/local/bin/jenkins-entrypoint.sh"]
