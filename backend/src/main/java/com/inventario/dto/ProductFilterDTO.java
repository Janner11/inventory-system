package com.inventario.dto;

import com.inventario.entity.ProductStatus;

import java.math.BigDecimal;

/**
 * Filtros opcionales para {@code ProductService.getAllProducts(Pageable, ProductFilterDTO)}.
 * Cualquier campo en {@code null} (o texto en blanco para {@code category}/{@code search}) se
 * ignora, salvo {@code status}: si no se especifica, se asume {@code ACTIVE} (ver ADR-001).
 */
public record ProductFilterDTO(
        ProductStatus status,
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String search
) {

    public static ProductFilterDTO empty() {
        return new ProductFilterDTO(null, null, null, null, null);
    }
}
