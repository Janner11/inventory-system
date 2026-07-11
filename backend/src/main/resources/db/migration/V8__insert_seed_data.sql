-- Datos de prueba (TEST-007): 10 productos variados + 20 movimientos de stock.
--
-- Nota de versionado (ADR-005: las migraciones ya ejecutadas son inmutables): el ticket
-- pedia literalmente "V5__insert_seed_data.sql", pero V5, V6 y V7 ya existen en el repo
-- (BACK-002 ampliacion y BACK-007) para cuando se escribio este ticket - se usa V8, el
-- siguiente numero disponible, en vez de reescribir migraciones ya aplicadas.
--
-- UUIDs fijos y legibles (no gen_random_uuid()) para que los movimientos de stock puedan
-- referenciar productos especificos por FK de forma deterministica, y para que
-- SeedDataTest pueda verificar valores exactos sin depender de un id generado en tiempo
-- de ejecucion.

INSERT INTO products (id, name, sku, description, category, price, quantity, min_stock, status, created_by) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Laptop Dell Inspiron 15', 'TEC-LAPTOP-001', 'Laptop 15" Intel Core i5, 16GB RAM, 512GB SSD', 'Electronica', 899.99, 15, 5, 'ACTIVE', 'seed'),
    ('a0000000-0000-0000-0000-000000000002', 'Mouse Logitech MX Master 3', 'TEC-MOUSE-002', 'Mouse inalambrico ergonomico', 'Electronica', 99.99, 3, 10, 'ACTIVE', 'seed'),
    ('a0000000-0000-0000-0000-000000000003', 'Teclado Mecanico Redragon', 'TEC-KEY-003', 'Teclado mecanico switches rojos, retroiluminado', 'Electronica', 45.50, 25, 8, 'ACTIVE', 'seed'),
    ('a0000000-0000-0000-0000-000000000004', 'Monitor Samsung 27 pulgadas', 'TEC-MON-004', 'Monitor Full HD 27" 75Hz', 'Electronica', 249.99, 8, 4, 'ACTIVE', 'seed'),
    ('a0000000-0000-0000-0000-000000000005', 'Silla Ergonomica Herman Miller', 'OFI-CHAIR-005', 'Silla de oficina ergonomica con soporte lumbar', 'Oficina', 1200.00, 2, 3, 'ACTIVE', 'seed'),
    ('a0000000-0000-0000-0000-000000000006', 'Escritorio Ajustable', 'OFI-DESK-006', 'Escritorio de altura ajustable electrico', 'Oficina', 450.00, 6, 2, 'ACTIVE', 'seed'),
    ('a0000000-0000-0000-0000-000000000007', 'Papel Bond Carta (resma)', 'OFI-PAPER-007', 'Resma de papel bond tamano carta, 500 hojas', 'Oficina', 5.99, 200, 50, 'ACTIVE', 'seed'),
    ('a0000000-0000-0000-0000-000000000008', 'Cafe Molido Premium 1kg', 'ALI-COFFEE-008', 'Cafe molido premium, tueste medio', 'Alimentos', 12.50, 40, 15, 'ACTIVE', 'seed'),
    ('a0000000-0000-0000-0000-000000000009', 'Impresora HP LaserJet', 'TEC-PRINT-009', 'Impresora laser monocromatica, red WiFi', 'Electronica', 320.00, 4, 5, 'ACTIVE', 'seed'),
    ('a0000000-0000-0000-0000-000000000010', 'Grapadora Industrial', 'OFI-STAPLE-010', 'Grapadora industrial de alta capacidad — descontinuada', 'Oficina', 22.00, 0, 5, 'INACTIVE', 'seed');

-- 20 movimientos de stock (2 por producto): una entrada inicial seguida de una salida o
-- ajuste que deja al producto en la cantidad con la que quedo sembrado arriba.
--
-- Invariante verificado contra StockService (registerEntry/registerExit/adjustStock): la
-- columna "quantity" SIEMPRE es exactamente new_quantity - previous_quantity, para los 3
-- tipos de movimiento. Para ENTRY es positiva (coincide con la cantidad pedida); para EXIT
-- es NEGATIVA (StockService.registerExit guarda "-request.quantity()", no la cantidad
-- solicitada tal cual); para ADJUSTMENT es el delta con signo. Verificado con
-- SeedDataTest.seed_movimientosTienenAritmeticaConsistenteEntreCantidades.

-- Producto 1: Laptop Dell Inspiron 15 (10 -> 20 -> 15)
INSERT INTO stock_movements (product_id, type, previous_quantity, new_quantity, quantity, reason, observations, performed_by, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'ENTRY', 10, 20, 10, 'Reposicion de inventario', 'Compra a proveedor TechSupply', 'admin@test.com', now() - interval '20 days'),
    ('a0000000-0000-0000-0000-000000000001', 'EXIT',  20, 15, -5, 'Venta', 'Venta a cliente corporativo', 'warehouse@test.com', now() - interval '5 days');

-- Producto 2: Mouse Logitech MX Master 3 (8 -> 13 -> 3) — termina en alerta de stock bajo
INSERT INTO stock_movements (product_id, type, previous_quantity, new_quantity, quantity, reason, observations, performed_by, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000002', 'ENTRY', 8, 13, 5, 'Reposicion de inventario', 'Compra a proveedor TechSupply', 'admin@test.com', now() - interval '18 days'),
    ('a0000000-0000-0000-0000-000000000002', 'EXIT',  13, 3, -10, 'Venta', 'Promocion fin de mes', 'warehouse@test.com', now() - interval '3 days');

-- Producto 3: Teclado Mecanico Redragon (15 -> 30 -> 25)
INSERT INTO stock_movements (product_id, type, previous_quantity, new_quantity, quantity, reason, observations, performed_by, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000003', 'ENTRY', 15, 30, 15, 'Reposicion de inventario', 'Compra a proveedor TechSupply', 'admin@test.com', now() - interval '17 days'),
    ('a0000000-0000-0000-0000-000000000003', 'EXIT',  30, 25, -5, 'Venta', 'Venta en tienda', 'warehouse@test.com', now() - interval '4 days');

-- Producto 4: Monitor Samsung 27" (12 -> 8, ajuste por conteo fisico)
INSERT INTO stock_movements (product_id, type, previous_quantity, new_quantity, quantity, reason, observations, performed_by, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000004', 'ENTRY',      6, 12, 6, 'Reposicion de inventario', 'Compra a proveedor DisplayCo', 'admin@test.com', now() - interval '15 days'),
    ('a0000000-0000-0000-0000-000000000004', 'ADJUSTMENT', 12, 8, -4, 'Ajuste por conteo fisico', 'Diferencia detectada en inventario trimestral', 'manager@test.com', now() - interval '2 days');

-- Producto 5: Silla Ergonomica Herman Miller (5 -> 2) — termina en alerta de stock bajo
INSERT INTO stock_movements (product_id, type, previous_quantity, new_quantity, quantity, reason, observations, performed_by, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000005', 'ENTRY', 0, 5, 5, 'Reposicion de inventario', 'Compra a proveedor OfficePro', 'admin@test.com', now() - interval '25 days'),
    ('a0000000-0000-0000-0000-000000000005', 'EXIT',  5, 2, -3, 'Venta', 'Venta a cliente corporativo', 'warehouse@test.com', now() - interval '6 days');

-- Producto 6: Escritorio Ajustable (10 -> 6)
INSERT INTO stock_movements (product_id, type, previous_quantity, new_quantity, quantity, reason, observations, performed_by, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000006', 'ENTRY', 4, 10, 6, 'Reposicion de inventario', 'Compra a proveedor OfficePro', 'admin@test.com', now() - interval '14 days'),
    ('a0000000-0000-0000-0000-000000000006', 'EXIT',  10, 6, -4, 'Venta', 'Venta en tienda', 'warehouse@test.com', now() - interval '1 days');

-- Producto 7: Papel Bond Carta (150 -> 250 -> 200)
INSERT INTO stock_movements (product_id, type, previous_quantity, new_quantity, quantity, reason, observations, performed_by, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000007', 'ENTRY', 150, 250, 100, 'Reposicion de inventario', 'Compra a proveedor PapelExpress', 'admin@test.com', now() - interval '12 days'),
    ('a0000000-0000-0000-0000-000000000007', 'EXIT',  250, 200, -50, 'Venta', 'Venta a mayorista', 'warehouse@test.com', now() - interval '3 days');

-- Producto 8: Cafe Molido Premium 1kg (30 -> 50 -> 40)
INSERT INTO stock_movements (product_id, type, previous_quantity, new_quantity, quantity, reason, observations, performed_by, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000008', 'ENTRY', 30, 50, 20, 'Reposicion de inventario', 'Compra a proveedor CafeReal', 'admin@test.com', now() - interval '10 days'),
    ('a0000000-0000-0000-0000-000000000008', 'EXIT',  50, 40, -10, 'Venta', 'Venta en tienda', 'warehouse@test.com', now() - interval '2 days');

-- Producto 9: Impresora HP LaserJet (7 -> 4) — termina en alerta de stock bajo
INSERT INTO stock_movements (product_id, type, previous_quantity, new_quantity, quantity, reason, observations, performed_by, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000009', 'ENTRY', 2, 7, 5, 'Reposicion de inventario', 'Compra a proveedor TechSupply', 'admin@test.com', now() - interval '22 days'),
    ('a0000000-0000-0000-0000-000000000009', 'EXIT',  7, 4, -3, 'Venta', 'Venta a cliente corporativo', 'warehouse@test.com', now() - interval '7 days');

-- Producto 10: Grapadora Industrial (10 -> 0, agotado y luego descontinuado/INACTIVE)
INSERT INTO stock_movements (product_id, type, previous_quantity, new_quantity, quantity, reason, observations, performed_by, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000010', 'ENTRY', 0, 10, 10, 'Reposicion de inventario', 'Ultima compra antes de descontinuar', 'admin@test.com', now() - interval '30 days'),
    ('a0000000-0000-0000-0000-000000000010', 'EXIT',  10, 0, -10, 'Venta', 'Liquidacion de inventario descontinuado', 'warehouse@test.com', now() - interval '9 days');
