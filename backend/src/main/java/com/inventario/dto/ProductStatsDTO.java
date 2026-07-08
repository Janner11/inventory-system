package com.inventario.dto;

import java.math.BigDecimal;

public record ProductStatsDTO(
        long totalProducts,
        long activeProducts,
        long inactiveProducts,
        long belowMinStockProducts,
        BigDecimal totalInventoryValue
) {
}
