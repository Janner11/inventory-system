package com.inventario.integration;

import com.inventario.config.JpaAuditingConfig;
import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import com.inventario.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class ProductRepositoryIntegrationTest {

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

    @Test
    void save_persistsProductWithGeneratedIdAndTimestamps() {
        Product product = buildProduct("LAP-001", "Laptop", ProductStatus.ACTIVE);

        Product saved = productRepository.save(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);
    }

    @Test
    void update_refreshesUpdatedAtAndIncrementsVersion() {
        Product saved = productRepository.saveAndFlush(buildProduct("UPD-100", "Router", ProductStatus.ACTIVE));
        var createdAt = saved.getCreatedAt();
        var firstUpdatedAt = saved.getUpdatedAt();

        saved.setQuantity(saved.getQuantity() + 1);
        Product updated = productRepository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(firstUpdatedAt);
    }

    @Test
    void save_normalizesSkuToTrimmedUppercase() {
        Product product = buildProduct("  mix-101  ", "Producto con SKU sin normalizar", ProductStatus.ACTIVE);

        Product saved = productRepository.saveAndFlush(product);

        assertThat(saved.getSku()).isEqualTo("MIX-101");
    }

    @Test
    void findBySku_withExistingSku_returnsProduct() {
        productRepository.save(buildProduct("EXA-102", "Producto exacto", ProductStatus.ACTIVE));

        Optional<Product> found = productRepository.findBySku("EXA-102");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Producto exacto");
    }

    @Test
    void existsBySku_withExistingAndNonExistingSku_returnsExpectedResult() {
        productRepository.save(buildProduct("EXI-103", "Producto existente", ProductStatus.ACTIVE));

        assertThat(productRepository.existsBySku("EXI-103")).isTrue();
        assertThat(productRepository.existsBySku("NO-EXISTE-999")).isFalse();
    }

    @Test
    void findByStatus_paginado_devuelveSoloProductosActivos() {
        productRepository.save(buildProduct("PAG-104", "Activo", ProductStatus.ACTIVE));
        productRepository.save(buildProduct("PAG-105", "Inactivo", ProductStatus.INACTIVE));

        Page<Product> page = productRepository.findByStatus(ProductStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getSku).contains("PAG-104");
        assertThat(page.getContent()).extracting(Product::getSku).doesNotContain("PAG-105");
    }

    @Test
    void findByCategoryIgnoreCase_paginado_ignoraMayusculasYMinusculas() {
        Product product = buildProduct("CAT-106", "Silla", ProductStatus.ACTIVE);
        product.setCategory("Muebles");
        productRepository.save(product);

        Page<Product> page = productRepository.findByCategoryIgnoreCase("muebles", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getSku).contains("CAT-106");
    }

    @Test
    void findByNameOrSkuContaining_paginado_encuentraPorTextoParcial() {
        productRepository.save(buildProduct("SEA-107", "Impresora Laser", ProductStatus.ACTIVE));
        Pageable pageable = PageRequest.of(0, 10);

        Page<Product> porNombre = productRepository
                .findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase("laser", "no-coincide", pageable);
        Page<Product> porSku = productRepository
                .findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase("no-coincide", "sea-107", pageable);

        assertThat(porNombre.getContent()).extracting(Product::getSku).contains("SEA-107");
        assertThat(porSku.getContent()).extracting(Product::getSku).contains("SEA-107");
    }

    @Test
    void findByPriceBetween_paginado_filtraPorRangoDePrecio() {
        Product barato = buildProduct("PRI-108", "Producto barato", ProductStatus.ACTIVE);
        barato.setPrice(new BigDecimal("5.00"));
        Product caro = buildProduct("PRI-109", "Producto caro", ProductStatus.ACTIVE);
        caro.setPrice(new BigDecimal("500.00"));
        productRepository.save(barato);
        productRepository.save(caro);

        Page<Product> page = productRepository.findByPriceBetween(
                new BigDecimal("1.00"), new BigDecimal("10.00"), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getSku).contains("PRI-108");
        assertThat(page.getContent()).extracting(Product::getSku).doesNotContain("PRI-109");
    }

    @Test
    void findByQuantityLessThanMinStockAndStatus_devuelveSoloProductosCriticosActivos() {
        Product critico = buildProduct("CRI-110", "Producto critico", ProductStatus.ACTIVE);
        critico.setQuantity(1);
        critico.setMinStock(5);
        Product normal = buildProduct("NOR-111", "Producto normal", ProductStatus.ACTIVE);
        normal.setQuantity(10);
        normal.setMinStock(2);
        Product criticoInactivo = buildProduct("CRI-112", "Critico pero inactivo", ProductStatus.INACTIVE);
        criticoInactivo.setQuantity(0);
        criticoInactivo.setMinStock(5);
        productRepository.save(critico);
        productRepository.save(normal);
        productRepository.save(criticoInactivo);

        List<Product> criticos = productRepository.findByQuantityLessThanMinStockAndStatus(ProductStatus.ACTIVE);

        assertThat(criticos).extracting(Product::getSku).contains("CRI-110");
        assertThat(criticos).extracting(Product::getSku).doesNotContain("NOR-111", "CRI-112");
    }

    @Test
    void findBySkuIgnoreCase_withExistingSku_returnsProduct() {
        productRepository.save(buildProduct("MOU-002", "Mouse", ProductStatus.ACTIVE));

        Optional<Product> found = productRepository.findBySkuIgnoreCase("mou-002");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Mouse");
    }

    @Test
    void findByStatus_returnsOnlyMatchingProducts() {
        productRepository.save(buildProduct("KEY-003", "Keyboard", ProductStatus.ACTIVE));
        productRepository.save(buildProduct("MON-004", "Monitor", ProductStatus.INACTIVE));

        List<Product> active = productRepository.findByStatus(ProductStatus.ACTIVE);

        // contains (no containsExactly): desde TEST-007, V8__insert_seed_data.sql siembra 9
        // productos ACTIVE mas via Flyway (que corre contra este mismo Testcontainers), asi
        // que la tabla ya no esta vacia al llegar a este test.
        assertThat(active).extracting(Product::getSku).contains("KEY-003");
        assertThat(active).extracting(Product::getSku).doesNotContain("MON-004");
    }

    @Test
    void save_withDuplicateSku_throwsDataIntegrityViolationException() {
        productRepository.saveAndFlush(buildProduct("DUP-005", "Producto original", ProductStatus.ACTIVE));

        assertThatThrownBy(() ->
                productRepository.saveAndFlush(buildProduct("DUP-005", "Producto duplicado", ProductStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Product buildProduct(String sku, String name, ProductStatus status) {
        Product product = new Product();
        product.setName(name);
        product.setSku(sku);
        product.setCategory("General");
        product.setPrice(new BigDecimal("10.00"));
        product.setQuantity(5);
        product.setMinStock(1);
        product.setStatus(status);
        return product;
    }
}
