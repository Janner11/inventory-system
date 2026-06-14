package com.inventario.integration;

import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import com.inventario.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
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

        assertThat(active).extracting(Product::getSku).containsExactly("KEY-003");
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
