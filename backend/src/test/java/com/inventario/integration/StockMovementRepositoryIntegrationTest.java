package com.inventario.integration;

import com.inventario.config.JpaAuditingConfig;
import com.inventario.entity.MovementType;
import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import com.inventario.entity.StockMovement;
import com.inventario.repository.ProductRepository;
import com.inventario.repository.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class StockMovementRepositoryIntegrationTest {

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
    void save_persistsStockMovementLinkedToProduct() {
        Product product = buildProduct("LAP-001", "Laptop");
        productRepository.saveAndFlush(product);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(MovementType.ENTRY);
        movement.setPreviousQuantity(5);
        movement.setNewQuantity(10);
        movement.setQuantity(5);
        movement.setReason("Reabastecimiento");
        movement.setObservations("Compra a proveedor");
        movement.setPerformedBy("admin@test.com");

        StockMovement saved = stockMovementRepository.saveAndFlush(movement);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getProduct().getId()).isEqualTo(product.getId());
    }

    @Test
    void findById_returnsMovementWithProductReference() {
        Product product = buildProduct("MOU-002", "Mouse");
        productRepository.saveAndFlush(product);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(MovementType.EXIT);
        movement.setPreviousQuantity(10);
        movement.setNewQuantity(7);
        movement.setQuantity(-3);
        movement.setPerformedBy("admin@test.com");

        StockMovement saved = stockMovementRepository.saveAndFlush(movement);

        Optional<StockMovement> found = stockMovementRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getType()).isEqualTo(MovementType.EXIT);
        assertThat(found.get().getProduct().getSku()).isEqualTo("MOU-002");
    }

    @Test
    void findByProductId_returnsOnlyMovementsOfThatProduct() {
        Product product = buildProduct("KEY-003", "Keyboard");
        productRepository.saveAndFlush(product);
        Product other = buildProduct("MON-004", "Monitor");
        productRepository.saveAndFlush(other);

        StockMovement first = saveMovement(product, MovementType.ENTRY, 5, 10, 5);
        StockMovement second = saveMovement(product, MovementType.EXIT, 10, 8, -2);
        saveMovement(other, MovementType.ENTRY, 5, 9, 4);

        Page<StockMovement> page = stockMovementRepository.findByProductId(product.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(StockMovement::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void findAllByOrderByCreatedAtDesc_returnsMovementsAcrossAllProducts() {
        Product product = buildProduct("PRI-005", "Impresora");
        productRepository.saveAndFlush(product);

        saveMovement(product, MovementType.ENTRY, 5, 10, 5);
        saveMovement(product, MovementType.EXIT, 10, 8, -2);

        Page<StockMovement> page = stockMovementRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void save_withZeroQuantity_violatesCheckConstraint() {
        Product product = buildProduct("ADJ-006", "Producto sin cambio");
        productRepository.saveAndFlush(product);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(MovementType.ADJUSTMENT);
        movement.setPreviousQuantity(5);
        movement.setNewQuantity(5);
        movement.setQuantity(0);
        movement.setPerformedBy("admin@test.com");

        assertThatThrownBy(() -> stockMovementRepository.saveAndFlush(movement))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findTopMovedProductsSince_ordersByMovementCountDescending() {
        Product mostMoved = buildProduct("TOP-001", "Producto mas movido");
        productRepository.saveAndFlush(mostMoved);
        Product lessMoved = buildProduct("TOP-002", "Producto menos movido");
        productRepository.saveAndFlush(lessMoved);

        saveMovement(mostMoved, MovementType.ENTRY, 0, 5, 5);
        saveMovement(mostMoved, MovementType.EXIT, 5, 3, -2);
        saveMovement(mostMoved, MovementType.ENTRY, 3, 8, 5);
        saveMovement(lessMoved, MovementType.ENTRY, 0, 4, 4);

        // Page grande + filtro por SKU propio (en vez de PageRequest.of(0, 5) + hasSize(2)):
        // desde TEST-007, V8__insert_seed_data.sql siembra otros productos con movimientos
        // propios via Flyway (que corre contra este mismo Testcontainers), asi que ya no se
        // puede asumir que TOP-001/TOP-002 sean los unicos ni que entren en el top 5 sin
        // competencia.
        List<Object[]> topProducts = stockMovementRepository.findTopMovedProductsSince(
                LocalDateTime.now().minusDays(30), PageRequest.of(0, 50));

        List<Object[]> propios = topProducts.stream()
                .filter(row -> "TOP-001".equals(row[1]) || "TOP-002".equals(row[1]))
                .toList();

        assertThat(propios).extracting(row -> row[0], row -> row[1], row -> row[3])
                .containsExactly(
                        tuple(mostMoved.getId(), "TOP-001", 3L),
                        tuple(lessMoved.getId(), "TOP-002", 1L));
    }

    @Test
    void findTopMovedProductsSince_excludesMovementsOutsideTheWindow() {
        Product product = buildProduct("TOP-003", "Producto reciente");
        productRepository.saveAndFlush(product);
        saveMovement(product, MovementType.ENTRY, 0, 5, 5);

        List<Object[]> topProducts = stockMovementRepository.findTopMovedProductsSince(
                LocalDateTime.now().plusDays(1), PageRequest.of(0, 5));

        assertThat(topProducts).isEmpty();
    }

    private StockMovement saveMovement(Product product, MovementType type, int previousQuantity, int newQuantity,
                                         int quantity) {
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(type);
        movement.setPreviousQuantity(previousQuantity);
        movement.setNewQuantity(newQuantity);
        movement.setQuantity(quantity);
        movement.setPerformedBy("admin@test.com");
        return stockMovementRepository.saveAndFlush(movement);
    }

    private Product buildProduct(String sku, String name) {
        Product product = new Product();
        product.setName(name);
        product.setSku(sku);
        product.setCategory("General");
        product.setPrice(new BigDecimal("10.00"));
        product.setQuantity(5);
        product.setMinStock(1);
        product.setStatus(ProductStatus.ACTIVE);
        return product;
    }
}
