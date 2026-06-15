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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
