-- Un movimiento de stock siempre debe representar un cambio real de cantidad
-- (ENTRY/EXIT ya lo garantizan por validacion de negocio en StockService, pero
-- faltaba el CHECK a nivel de base de datos para ADJUSTMENT). BACK-005.
ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_quantity_not_zero CHECK (quantity <> 0);
