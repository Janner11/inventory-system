package com.inventario.integration;

import com.inventario.config.JpaAuditingConfig;
import com.inventario.dto.ProductRevisionDTO;
import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import com.inventario.exception.ProductNotFoundException;
import com.inventario.repository.ProductRepository;
import com.inventario.service.AuditService;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditService.class})
@Testcontainers
class AuditServiceIntegrationTest {

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
    private EntityManager entityManager;

    @Autowired
    private AuditService auditService;

    @Test
    void getProductRevisions_afterCreateAndUpdate_returnsRevisionsInOrder() {
        Product product = new Product();
        product.setName("Laptop");
        product.setSku("AUD-REV-001");
        product.setCategory("Electronica");
        product.setPrice(new BigDecimal("1500.00"));
        product.setQuantity(10);
        product.setMinStock(2);
        product.setStatus(ProductStatus.ACTIVE);

        Product saved = productRepository.saveAndFlush(product);
        UUID id = saved.getId();

        // Envers groups all changes within one transaction into a single revision,
        // so the ADD and MOD must be committed as separate transactions.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        TestTransaction.start();
        Product toUpdate = productRepository.findById(id).orElseThrow();
        toUpdate.setQuantity(8);
        productRepository.saveAndFlush(toUpdate);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        TestTransaction.start();
        entityManager.clear();

        List<ProductRevisionDTO> revisions = auditService.getProductRevisions(id);

        assertThat(revisions).hasSize(2);

        ProductRevisionDTO addRevision = revisions.get(0);
        assertThat(addRevision.revisionType()).isEqualTo(RevisionType.ADD);
        assertThat(addRevision.quantity()).isEqualTo(10);
        assertThat(addRevision.sku()).isEqualTo("AUD-REV-001");
        assertThat(addRevision.revisionTimestamp()).isNotNull();

        ProductRevisionDTO modRevision = revisions.get(1);
        assertThat(modRevision.revisionType()).isEqualTo(RevisionType.MOD);
        assertThat(modRevision.quantity()).isEqualTo(8);
        assertThat(modRevision.revisionNumber()).isGreaterThan(addRevision.revisionNumber());
    }

    @Test
    void getProductRevisions_withNonExistingProduct_throwsProductNotFoundException() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> auditService.getProductRevisions(id))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
