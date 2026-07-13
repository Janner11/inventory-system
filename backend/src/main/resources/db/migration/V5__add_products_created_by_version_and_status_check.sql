-- Completa el modelo de datos de products (BACK-002): agrega created_by y
-- version (optimistic locking), ya documentados en el modelo de datos del
-- proyecto pero ausentes en V2, y agrega el CHECK de status que
-- products_aud (V3) ya tenia pero products no.
ALTER TABLE products
    ADD COLUMN created_by VARCHAR(150),
    ADD COLUMN version    BIGINT NOT NULL DEFAULT 0;

ALTER TABLE products
    ADD CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

-- Indice parcial para acelerar la busqueda de productos criticos (stock bajo)
-- sobre productos activos (ver ProductRepository.findByQuantityLessThanMinStockAndStatus).
CREATE INDEX idx_products_low_stock ON products (quantity, min_stock) WHERE status = 'ACTIVE';
