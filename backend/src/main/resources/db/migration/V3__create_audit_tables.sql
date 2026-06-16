-- Tablas de auditoria de Hibernate Envers para la entidad Product (BACK-003,
-- adelantado desde BACK-007). El DDL fue generado con
-- `spring.jpa.hibernate.ddl-auto=create` contra una base de datos temporal y
-- adaptado aqui para que `ddl-auto=validate` valide el esquema sin errores.

-- Tabla de revisiones de Envers: una fila por cada operacion auditada
-- (INSERT/UPDATE/DELETE), independientemente de la entidad.
CREATE TABLE revinfo (
    rev      INTEGER PRIMARY KEY,
    revtstmp BIGINT
);

CREATE SEQUENCE revinfo_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Tabla de auditoria de products: una fila por revision de cada producto.
CREATE TABLE products_aud (
    id          UUID NOT NULL,
    name        VARCHAR(200),
    sku         VARCHAR(50),
    description VARCHAR(1000),
    category    VARCHAR(100),
    price       NUMERIC(10, 2),
    quantity    INTEGER,
    min_stock   INTEGER,
    status      VARCHAR(20),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    rev         INTEGER NOT NULL,
    revtype     SMALLINT,
    CONSTRAINT products_aud_pkey PRIMARY KEY (rev, id),
    CONSTRAINT fk_products_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev),
    CONSTRAINT products_aud_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
