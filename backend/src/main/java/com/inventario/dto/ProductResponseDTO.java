package com.inventario.dto;

import com.inventario.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponseDTO(
        UUID id,
        String name,
        String sku,
        String description,
        String category,
        BigDecimal price,
        Integer quantity,
        Integer minStock,
        ProductStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
