-- BACK-007 (ampliacion): registra que usuario realizo cada revision de Envers.
-- Antes revinfo solo guardaba el timestamp (V3); esta columna es poblada por
-- AuditRevisionListener a partir del JWT autenticado (claim preferred_username).
-- Las revisiones existentes quedan con username NULL (no hay forma de reconstruir
-- ese dato retroactivamente).
ALTER TABLE revinfo ADD COLUMN username VARCHAR(150);
