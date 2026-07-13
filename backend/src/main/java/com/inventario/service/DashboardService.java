package com.inventario.service;

import com.inventario.dto.DashboardSummaryDTO;
import com.inventario.dto.ProductResponseDTO;
import com.inventario.dto.ProductStatsDTO;
import com.inventario.dto.StockMovementResponseDTO;
import com.inventario.dto.TopProductDTO;
import com.inventario.repository.StockMovementRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Agrega datos ya calculados por {@link ProductService}/{@link StockService} (KPIs, alertas de
 * stock, movimientos recientes) en las vistas que consume el dashboard, y anade la unica
 * consulta que no existia todavia: productos mas movidos en una ventana de tiempo.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int RECENT_MOVEMENTS_LIMIT = 10;
    private static final int TOP_PRODUCTS_LIMIT = 5;
    private static final long TOP_PRODUCTS_WINDOW_DAYS = 30;

    private final ProductService productService;
    private final StockService stockService;
    private final StockMovementRepository stockMovementRepository;

    public DashboardService(ProductService productService, StockService stockService,
                             StockMovementRepository stockMovementRepository) {
        this.productService = productService;
        this.stockService = stockService;
        this.stockMovementRepository = stockMovementRepository;
    }

    public DashboardSummaryDTO getSummary() {
        ProductStatsDTO stats = productService.getProductStats();
        return new DashboardSummaryDTO(
                stats.totalProducts(),
                stats.activeProducts(),
                stats.inactiveProducts(),
                stats.belowMinStockProducts(),
                stats.totalInventoryValue(),
                stockMovementRepository.count());
    }

    public List<ProductResponseDTO> getCriticalProducts() {
        return productService.getProductsBelowMinStock();
    }

    public List<StockMovementResponseDTO> getRecentMovements() {
        return stockService.getRecentMovements(PageRequest.of(0, RECENT_MOVEMENTS_LIMIT)).getContent();
    }

    public List<TopProductDTO> getTopMovedProducts() {
        LocalDateTime since = LocalDateTime.now().minusDays(TOP_PRODUCTS_WINDOW_DAYS);

        return stockMovementRepository.findTopMovedProductsSince(since, PageRequest.of(0, TOP_PRODUCTS_LIMIT))
                .stream()
                .map(DashboardService::toTopProductDTO)
                .toList();
    }

    private static TopProductDTO toTopProductDTO(Object[] row) {
        return new TopProductDTO((UUID) row[0], (String) row[1], (String) row[2], (Long) row[3]);
    }
}
