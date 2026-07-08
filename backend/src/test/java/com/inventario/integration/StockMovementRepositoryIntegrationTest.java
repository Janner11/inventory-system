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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
