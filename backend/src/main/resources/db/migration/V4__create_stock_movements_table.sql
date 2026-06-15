-- Tabla de movimientos de stock del inventario (BACK-005).
CREATE TABLE stock_movements (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id        UUID NOT NULL REFERENCES products (id),
    type              VARCHAR(20) NOT NULL,
    previous_quantity INTEGER NOT NULL,
    new_quantity      INTEGER NOT NULL,
    quantity          INTEGER NOT NULL,
    reason            VARCHAR(200),
    observations      VARCHAR(500),
    performed_by      VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_stock_movements_type CHECK (type IN ('ENTRY', 'EXIT', 'ADJUSTMENT')),
    CONSTRAINT chk_stock_movements_previous_quantity_non_negative CHECK (previous_quantity >= 0),
    CONSTRAINT chk_stock_movements_new_quantity_non_negative CHECK (new_quantity >= 0)
);

CREATE INDEX idx_stock_movements_product_id ON stock_movements (product_id);
CREATE INDEX idx_stock_movements_created_at ON stock_movements (created_at);
