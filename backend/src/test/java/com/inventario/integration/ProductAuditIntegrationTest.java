package com.inventario.integration;

import com.inventario.config.JpaAuditingConfig;
import com.inventario.entity.Product;
import com.inventario.entity.ProductStatus;
import com.inventario.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class ProductAuditIntegrationTest {

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

    @Test
    void saveAndUpdateProduct_createsAddAndModRevisions() {
        Product product = new Product();
        product.setName("Laptop");
        product.setSku("AUD-001");
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

        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        @SuppressWarnings("unchecked") // forRevisionsOfEntity() returns a raw List per the Envers API
        List<Object[]> revisions = auditReader.createQuery()
                .forRevisionsOfEntity(Product.class, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        assertThat(revisions).hasSize(2);

        Product addRevision = (Product) revisions.get(0)[0];
        RevisionType addType = (RevisionType) revisions.get(0)[2];
        Product modRevision = (Product) revisions.get(1)[0];
        RevisionType modType = (RevisionType) revisions.get(1)[2];

        assertThat(addRevision.getQuantity()).isEqualTo(10);
        assertThat(addType).isEqualTo(RevisionType.ADD);

        assertThat(modRevision.getQuantity()).isEqualTo(8);
        assertThat(modType).isEqualTo(RevisionType.MOD);
    }
}
