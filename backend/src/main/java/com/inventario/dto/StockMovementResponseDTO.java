package com.inventario.dto;

import com.inventario.entity.MovementType;

import java.time.LocalDateTime;
import java.util.UUID;

public record StockMovementResponseDTO(
        UUID id,
        UUID productId,
        String productSku,
        String productName,
        MovementType type,
        Integer previousQuantity,
        Integer newQuantity,
        Integer quantity,
        String reason,
        String observations,
        String performedBy,
        LocalDateTime createdAt
) {
}
