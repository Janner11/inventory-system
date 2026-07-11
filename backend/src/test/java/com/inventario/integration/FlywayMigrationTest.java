package com.inventario.integration;

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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que Flyway aplique todas las migraciones (V1-V8, TEST-007 agrega V8) sin errores
 * contra un PostgreSQL real (Testcontainers), que las 5 tablas del esquema existan, y que
 * {@code products} tenga el esquema esperado por {@link com.inventario.entity.Product}
 * (BACK-002).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class FlywayMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventario_flyway_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void flyway_aplicaV1AV8ExitosamenteYEnOrden() throws Exception {
        List<String> versions = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank")) {
            while (resultSet.next()) {
                assertThat(resultSet.getBoolean("success")).isTrue();
                versions.add(resultSet.getString("version"));
            }
        }

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
    }

    @Test
    void tablaProducts_tieneLasColumnasEsperadasPorLaEntidad() throws Exception {
        Set<String> columns = tableColumns("products");

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "name", "sku", "description", "category", "price", "quantity",
                "min_stock", "status", "created_at", "updated_at", "created_by", "version");
    }

    @Test
    void todasLasTablasDelEsquemaExisten() throws Exception {
        Set<String> tables = new HashSet<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("table_name"));
            }
        }

        // Las 5 tablas de negocio/auditoria del modelo de datos (seccion 5 de CLAUDE.md) +
        // flyway_schema_history (la propia tabla de control de Flyway).
        assertThat(tables).contains(
                "products", "stock_movements", "products_aud", "revinfo", "flyway_schema_history");
    }

    @Test
    void tablaStockMovements_tieneLasColumnasEsperadasPorLaEntidad() throws Exception {
        Set<String> columns = tableColumns("stock_movements");

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "product_id", "type", "previous_quantity", "new_quantity", "quantity",
                "reason", "observations", "performed_by", "created_at");
    }

    @Test
    void tablaProductsAud_tieneLasColumnasEsperadasPorEnvers() throws Exception {
        Set<String> columns = tableColumns("products_aud");

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "name", "sku", "description", "category", "price", "quantity",
                "min_stock", "status", "created_at", "updated_at", "rev", "revtype");
    }

    @Test
    void tablaRevinfo_tieneLaColumnaUsernameAgregadaPorV7() throws Exception {
        Set<String> columns = tableColumns("revinfo");

        assertThat(columns).containsExactlyInAnyOrder("rev", "revtstmp", "username");
    }

    private Set<String> tableColumns(String tableName) throws Exception {
        Set<String> columns = new HashSet<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT column_name FROM information_schema.columns WHERE table_name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString("column_name"));
                }
            }
        }

        return columns;
    }
}
