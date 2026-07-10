package com.inventario.dto;

import java.math.BigDecimal;

public record DashboardSummaryDTO(
        long totalProducts,
        long activeProducts,
        long inactiveProducts,
        long belowMinStockProducts,
        BigDecimal totalInventoryValue,
        long totalStockMovements
) {
}
