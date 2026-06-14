-- Tabla de productos del inventario (BACK-002).
CREATE TABLE products (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL,
    sku         VARCHAR(50) NOT NULL,
    description VARCHAR(1000),
    category    VARCHAR(100) NOT NULL,
    price       NUMERIC(10, 2) NOT NULL,
    quantity    INTEGER NOT NULL,
    min_stock   INTEGER NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT chk_products_price_positive CHECK (price > 0),
    CONSTRAINT chk_products_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT chk_products_min_stock_non_negative CHECK (min_stock >= 0)
);

CREATE INDEX idx_products_category ON products (category);
CREATE INDEX idx_products_status ON products (status);
