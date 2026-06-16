#!/bin/sh
# Crea la base de datos dedicada para Keycloak dentro de la misma instancia
# de PostgreSQL usada por el backend. Se ejecuta solo en la primera
# inicialización del volumen de datos (docker-entrypoint-initdb.d).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE keycloak'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
EOSQL
