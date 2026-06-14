-- Migracion base: confirma que Flyway corre correctamente contra la base de datos
-- y habilita extensiones estandar usadas por las tablas de los proximos tickets (BACK-002+).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
