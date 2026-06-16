package com.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StockMovementRequestDTO(
        @NotNull
        UUID productId,

        @NotNull
        @Positive
        Integer quantity,

        @Size(max = 200)
        String reason,

        @Size(max = 500)
        String observations,

        @NotBlank
        String performedBy
) {
}
