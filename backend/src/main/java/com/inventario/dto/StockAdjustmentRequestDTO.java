package com.inventario.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StockAdjustmentRequestDTO(
        @NotNull
        UUID productId,

        @NotNull
        @Min(0)
        Integer newQuantity,

        @Size(max = 200)
        String reason,

        @Size(max = 500)
        String observations,

        @NotBlank
        String performedBy
) {
}
