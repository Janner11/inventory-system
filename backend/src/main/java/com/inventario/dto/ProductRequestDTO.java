package com.inventario.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequestDTO(
        @NotBlank
        @Size(max = 200)
        String name,

        @NotBlank
        @Size(max = 50)
        String sku,

        @Size(max = 1000)
        String description,

        @NotBlank
        @Size(max = 100)
        String category,

        @NotNull
        @Positive
        BigDecimal price,

        @NotNull
        @Min(0)
        Integer quantity,

        @NotNull
        @Min(0)
        Integer minStock
) {
}
