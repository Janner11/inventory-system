#!/bin/bash
# CICD-002: se ejecuta como root (ver "USER root" en el Dockerfile) para poder alinear
# el grupo del usuario "jenkins" con el GID real del socket de Docker montado desde el
# host (distinto en cada maquina/entorno - Docker Desktop en macOS vs. Docker Engine en
# Linux CI), y despues baja privilegios al usuario "jenkins" para arrancar Jenkins de
# verdad, tal como lo hace la imagen oficial.
set -euo pipefail

DOCKER_SOCK=/var/run/docker.sock

if [ -S "$DOCKER_SOCK" ]; then
  SOCK_GID=$(stat -c '%g' "$DOCKER_SOCK")
  if ! getent group "$SOCK_GID" > /dev/null 2>&1; then
    groupadd -g "$SOCK_GID" docker-host
  fi
  SOCK_GROUP=$(getent group "$SOCK_GID" | cut -d: -f1)
  usermod -aG "$SOCK_GROUP" jenkins
  echo "jenkins-entrypoint: usuario 'jenkins' agregado al grupo '${SOCK_GROUP}' (gid=${SOCK_GID}) del socket de Docker"
else
  echo "jenkins-entrypoint: ADVERTENCIA - ${DOCKER_SOCK} no encontrado; los stages que usan Docker (Build Docker Images, Deploy Staging, Security Scan) fallaran" >&2
fi

exec gosu jenkins /usr/local/bin/jenkins.sh "$@"
