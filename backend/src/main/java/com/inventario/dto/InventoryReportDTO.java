package com.inventario.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Reporte consolidado de inventario: agrega en un solo JSON los datos que el dashboard expone por separado. */
public record InventoryReportDTO(
        LocalDateTime generatedAt,
        DashboardSummaryDTO summary,
        List<ProductResponseDTO> criticalProducts,
        List<StockMovementResponseDTO> recentMovements,
        List<TopProductDTO> topProducts
) {
}
