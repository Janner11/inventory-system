package com.inventario.dto;

import com.inventario.entity.ProductStatus;
import org.hibernate.envers.RevisionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductRevisionDTO(
        int revisionNumber,
        LocalDateTime revisionTimestamp,
        RevisionType revisionType,
        UUID id,
        String name,
        String sku,
        String description,
        String category,
        BigDecimal price,
        Integer quantity,
        Integer minStock,
        ProductStatus status
) {
}
