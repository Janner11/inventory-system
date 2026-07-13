package com.inventario.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST-007: verifica que los constraints de la base de datos (definidos en las migraciones
 * Flyway, no las anotaciones de validacion de Java como {@code @Positive}/{@code @NotBlank})
 * realmente esten activos, insertando directamente por JDBC datos que deberian ser
 * rechazados por Postgres. A diferencia de {@code ProductRepositoryIntegrationTest}/
 * {@code StockMovementRepositoryIntegrationTest} (que pasan por JPA/Hibernate), estos tests
 * usan SQL crudo para probar el ultimo nivel de defensa: el esquema en si, que debe seguir
 * protegiendo la integridad de los datos incluso si algun otro camino de codigo (una
 * migracion de datos, un script, un bug que se salte la capa de servicio) llega a intentar
 * una escritura invalida.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DataIntegrityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    private static final String INSERT_PRODUCT = """
            INSERT INTO products (name, sku, category, price, quantity, min_stock, status)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    @AfterEach
    void limpiarProductosDePrueba() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            // Primero los movimientos (la FK stock_movements_product_id_fkey impide borrar
            // un producto que todavia tiene movimientos asociados, ej. de
            // eliminarProductoConMovimientosDeStock_fallaPorForeignKey).
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM stock_movements
                    WHERE product_id IN (SELECT id FROM products WHERE sku LIKE 'DATAINTEGRITY-%')
                    """)) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM products WHERE sku LIKE 'DATAINTEGRITY-%'")) {
                statement.executeUpdate();
            }
        }
    }

    private void insertProduct(String sku, double price, int quantity, int minStock, String status) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_PRODUCT)) {
            statement.setString(1, "Producto de prueba");
            statement.setString(2, sku);
            statement.setString(3, "Test");
            statement.setBigDecimal(4, java.math.BigDecimal.valueOf(price));
            statement.setInt(5, quantity);
            statement.setInt(6, minStock);
            statement.setString(7, status);
            statement.executeUpdate();
        }
    }

    @Test
    void insertarProductoConSkuDuplicado_fallaPorConstraintUnique() throws SQLException {
        insertProduct("DATAINTEGRITY-DUP-001", 10.00, 5, 1, "ACTIVE");

        assertThatThrownBy(() -> insertProduct("DATAINTEGRITY-DUP-001", 20.00, 3, 1, "ACTIVE"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uq_products_sku");
    }

    @Test
    void insertarProductoConPrecioNegativo_fallaPorCheckConstraint() {
        assertThatThrownBy(() -> insertProduct("DATAINTEGRITY-PRICE-NEG", -10.00, 5, 1, "ACTIVE"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_products_price_positive");
    }

    @Test
    void insertarProductoConPrecioCero_fallaPorCheckConstraint() {
        assertThatThrownBy(() -> insertProduct("DATAINTEGRITY-PRICE-ZERO", 0.00, 5, 1, "ACTIVE"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_products_price_positive");
    }

    @Test
    void insertarProductoConCantidadNegativa_fallaPorCheckConstraint() {
        assertThatThrownBy(() -> insertProduct("DATAINTEGRITY-QTY-NEG", 10.00, -1, 1, "ACTIVE"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_products_quantity_non_negative");
    }

    @Test
    void insertarProductoConStockMinimoNegativo_fallaPorCheckConstraint() {
        assertThatThrownBy(() -> insertProduct("DATAINTEGRITY-MINSTOCK-NEG", 10.00, 5, -1, "ACTIVE"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_products_min_stock_non_negative");
    }

    @Test
    void insertarProductoConStatusInvalido_fallaPorCheckConstraint() {
        assertThatThrownBy(() -> insertProduct("DATAINTEGRITY-STATUS-BAD", 10.00, 5, 1, "BORRADO"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_products_status");
    }

    @Test
    void insertarMovimientoDeStock_conProductIdInexistente_fallaPorForeignKey() {
        UUID productoInexistente = UUID.randomUUID();

        assertThatThrownBy(() -> insertStockMovement(
                productoInexistente, "ENTRY", 0, 10, 10))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("stock_movements_product_id_fkey");
    }

    @Test
    void insertarMovimientoDeStock_conTipoInvalido_fallaPorCheckConstraint() throws SQLException {
        UUID productId = insertProductAndReturnId("DATAINTEGRITY-STOCK-TYPE", 10.00, 5, 1, "ACTIVE");

        assertThatThrownBy(() -> insertStockMovement(productId, "TRANSFERENCIA", 5, 10, 5))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_stock_movements_type");
    }

    @Test
    void insertarMovimientoDeStock_conCantidadCero_fallaPorCheckConstraint() throws SQLException {
        UUID productId = insertProductAndReturnId("DATAINTEGRITY-STOCK-ZERO", 10.00, 5, 1, "ACTIVE");

        assertThatThrownBy(() -> insertStockMovement(productId, "ADJUSTMENT", 5, 5, 0))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_stock_movements_quantity_not_zero");
    }

    @Test
    void insertarMovimientoDeStock_conNewQuantityNegativa_fallaPorCheckConstraint() throws SQLException {
        UUID productId = insertProductAndReturnId("DATAINTEGRITY-STOCK-NEGQTY", 10.00, 5, 1, "ACTIVE");

        assertThatThrownBy(() -> insertStockMovement(productId, "EXIT", 5, -1, -6))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("chk_stock_movements_new_quantity_non_negative");
    }

    @Test
    void eliminarProductoConMovimientosDeStock_fallaPorForeignKey() throws SQLException {
        UUID productId = insertProductAndReturnId("DATAINTEGRITY-STOCK-FK-PROTECT", 10.00, 5, 1, "ACTIVE");
        insertStockMovement(productId, "ENTRY", 0, 5, 5);

        assertThatThrownBy(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM products WHERE id = ?")) {
                statement.setObject(1, productId);
                statement.executeUpdate();
            }
        }).isInstanceOf(SQLException.class)
                .hasMessageContaining("stock_movements_product_id_fkey");
    }

    private UUID insertProductAndReturnId(String sku, double price, int quantity, int minStock, String status)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     INSERT_PRODUCT + " RETURNING id")) {
            statement.setString(1, "Producto de prueba");
            statement.setString(2, sku);
            statement.setString(3, "Test");
            statement.setBigDecimal(4, java.math.BigDecimal.valueOf(price));
            statement.setInt(5, quantity);
            statement.setInt(6, minStock);
            statement.setString(7, status);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("id");
            }
        }
    }

    private void insertStockMovement(UUID productId, String type, int previousQuantity, int newQuantity, int quantity)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO stock_movements
                         (product_id, type, previous_quantity, new_quantity, quantity, performed_by)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setObject(1, productId);
            statement.setString(2, type);
            statement.setInt(3, previousQuantity);
            statement.setInt(4, newQuantity);
            statement.setInt(5, quantity);
            statement.setString(6, "test");
            statement.executeUpdate();
        }
    }
}
