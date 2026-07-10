package com.inventario.service;

import com.inventario.dto.ProductRevisionDTO;
import com.inventario.entity.Product;
import com.inventario.exception.ProductNotFoundException;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuditService {

    private final EntityManager entityManager;

    public AuditService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked") // forRevisionsOfEntity() returns a raw List per the Envers API
    public List<ProductRevisionDTO> getProductRevisions(UUID id) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        List<Object[]> revisions = auditReader.createQuery()
                .forRevisionsOfEntity(Product.class, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        if (revisions.isEmpty()) {
            throw new ProductNotFoundException(id);
        }

        return revisions.stream()
                .map(this::toRevisionDTO)
                .toList();
    }

    private ProductRevisionDTO toRevisionDTO(Object[] revision) {
        Product product = (Product) revision[0];
        DefaultRevisionEntity revisionEntity = (DefaultRevisionEntity) revision[1];
        RevisionType revisionType = (RevisionType) revision[2];

        LocalDateTime revisionTimestamp = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(revisionEntity.getTimestamp()), ZoneId.systemDefault());

        return new ProductRevisionDTO(
                revisionEntity.getId(),
                revisionTimestamp,
                revisionType,
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice(),
                product.getQuantity(),
                product.getMinStock(),
                product.getStatus()
        );
    }
}
