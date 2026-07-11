package com.inventario.integration;

import com.inventario.entity.Product;
import com.inventario.entity.StockMovement;
import com.inventario.repository.ProductRepository;
import com.inventario.repository.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEST-007: verifica que los datos sembrados por V8__insert_seed_data.sql sean correctos y
 * reproducibles en un ambiente limpio — Testcontainers arranca un Postgres vacio y Flyway
 * corre TODAS las migraciones (V1-V8) contra el, incluyendo el seed, exactamente como
 * pasaria en cualquier ambiente nuevo (dev de otro integrante del equipo, CI, staging).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(com.inventario.config.JpaAuditingConfig.class)
@Testcontainers
class SeedDataTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Test
    void seed_insertaExactamenteDiezProductos() {
        List<Product> productos = productRepository.findAll();

        assertThat(productos).hasSize(10);
    }

    @Test
    void seed_insertaExactamenteVeinteMovimientosDeStock() {
        List<StockMovement> movimientos = stockMovementRepository.findAll();

        assertThat(movimientos).hasSize(20);
    }

    @Test
    void seed_todosLosSkusSonUnicos() {
        List<Product> productos = productRepository.findAll();
        List<String> skus = productos.stream().map(Product::getSku).collect(Collectors.toList());

        assertThat(skus).doesNotHaveDuplicates();
        assertThat(new java.util.HashSet<>(skus)).hasSize(10);
    }

    @Test
    void seed_todosLosProductosTienenPrecioPositivoYCantidadesNoNegativas() {
        List<Product> productos = productRepository.findAll();

        assertThat(productos).allSatisfy(producto -> {
            assertThat(producto.getPrice()).isGreaterThan(BigDecimal.ZERO);
            assertThat(producto.getQuantity()).isGreaterThanOrEqualTo(0);
            assertThat(producto.getMinStock()).isGreaterThanOrEqualTo(0);
            assertThat(producto.getName()).isNotBlank();
            assertThat(producto.getCategory()).isNotBlank();
        });
    }

    @Test
    void seed_incluyeVariasCategorias() {
        List<Product> productos = productRepository.findAll();
        List<String> categorias = productos.stream().map(Product::getCategory).distinct().toList();

        // El ticket pide "10 productos de ejemplo variados" - al menos 3 categorias distintas
        // confirma que el seed no es solo repetir el mismo tipo de producto.
        assertThat(categorias).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void seed_incluyeAlMenosUnProductoInactivo() {
        List<Product> inactivos = productRepository.findByStatus(com.inventario.entity.ProductStatus.INACTIVE);

        assertThat(inactivos).isNotEmpty();
    }

    @Test
    void seed_incluyeProductosConStockBajoElMinimo() {
        List<Product> criticos = productRepository
                .findByQuantityLessThanMinStockAndStatus(com.inventario.entity.ProductStatus.ACTIVE);

        // Los productos con quantity < minStock alimentan /api/products/critical y el
        // dashboard (BACK-008) - el seed deliberadamente incluye varios para que esas
        // pantallas no se vean vacias en un ambiente recien sembrado.
        assertThat(criticos).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void seed_todosLosMovimientosReferencianProductosExistentes() {
        List<StockMovement> movimientos = stockMovementRepository.findAll();
        List<Product> productos = productRepository.findAll();
        List<java.util.UUID> idsDeProductos = productos.stream().map(Product::getId).toList();

        assertThat(movimientos).allSatisfy(movimiento ->
                assertThat(idsDeProductos).contains(movimiento.getProduct().getId()));
    }

    @Test
    void seed_cadaProductoTieneAlMenosUnMovimientoDeStock() {
        List<Product> productos = productRepository.findAll();
        List<StockMovement> movimientos = stockMovementRepository.findAll();
        List<java.util.UUID> productosConMovimientos = movimientos.stream()
                .map(m -> m.getProduct().getId())
                .distinct()
                .toList();

        assertThat(productosConMovimientos).hasSize(10);
        assertThat(productos).allSatisfy(producto ->
                assertThat(productosConMovimientos).contains(producto.getId()));
    }

    @Test
    void seed_movimientosTienenAritmeticaConsistenteEntreCantidades() {
        List<StockMovement> movimientos = stockMovementRepository.findAll();

        assertThat(movimientos).allSatisfy(movimiento -> {
            int esperado = movimiento.getNewQuantity() - movimiento.getPreviousQuantity();
            assertThat(movimiento.getQuantity())
                    .as("movimiento %s: new(%d) - previous(%d) debe ser igual a quantity",
                            movimiento.getId(), movimiento.getNewQuantity(), movimiento.getPreviousQuantity())
                    .isEqualTo(esperado);
        });
    }

    @Test
    void seed_esReproducibleEnUnAmbienteLimpio() {
        // Este mismo test corriendo con exito ya es la prueba: Testcontainers arranco un
        // Postgres vacio en este metodo de clase, Flyway corrio las 8 migraciones desde
        // cero (incluyendo V8) sin ningun error, y los asserts de arriba confirman que los
        // datos resultantes son exactamente los esperados - la definicion misma de
        // "seeds reproducibles en ambientes limpios" (Validaciones del ticket).
        assertThat(productRepository.count()).isEqualTo(10);
        assertThat(stockMovementRepository.count()).isEqualTo(20);
    }
}
