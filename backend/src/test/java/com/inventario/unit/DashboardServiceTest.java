package com.inventario.unit;

import com.inventario.dto.DashboardSummaryDTO;
import com.inventario.dto.ProductResponseDTO;
import com.inventario.dto.ProductStatsDTO;
import com.inventario.dto.StockMovementResponseDTO;
import com.inventario.dto.TopProductDTO;
import com.inventario.entity.MovementType;
import com.inventario.entity.ProductStatus;
import com.inventario.repository.StockMovementRepository;
import com.inventario.service.DashboardService;
import com.inventario.service.ProductService;
import com.inventario.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private StockService stockService;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(productService, stockService, stockMovementRepository);
    }

    @Test
    void getSummary_combinesProductStatsAndTotalMovements() {
        when(productService.getProductStats()).thenReturn(
                new ProductStatsDTO(10, 8, 2, 3, new BigDecimal("1500.00")));
        when(stockMovementRepository.count()).thenReturn(42L);

        DashboardSummaryDTO summary = dashboardService.getSummary();

        assertThat(summary.totalProducts()).isEqualTo(10);
        assertThat(summary.activeProducts()).isEqualTo(8);
        assertThat(summary.inactiveProducts()).isEqualTo(2);
        assertThat(summary.belowMinStockProducts()).isEqualTo(3);
        assertThat(summary.totalInventoryValue()).isEqualByComparingTo("1500.00");
        assertThat(summary.totalStockMovements()).isEqualTo(42);
    }

    @Test
    void getCriticalProducts_delegatesToProductService() {
        ProductResponseDTO product = buildProduct("Laptop", "LAP-001");
        when(productService.getProductsBelowMinStock()).thenReturn(List.of(product));

        List<ProductResponseDTO> result = dashboardService.getCriticalProducts();

        assertThat(result).containsExactly(product);
    }

    @Test
    void getRecentMovements_requestsTop10MostRecentFirst() {
        StockMovementResponseDTO movement = buildMovement();
        when(stockService.getRecentMovements(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(movement)));

        List<StockMovementResponseDTO> result = dashboardService.getRecentMovements();

        assertThat(result).containsExactly(movement);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(stockService).getRecentMovements(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    }

    @Test
    void getTopMovedProducts_mapsRowsToDTOsPreservingOrder() {
        UUID productId = UUID.randomUUID();
        Object[] row = {productId, "LAP-001", "Laptop", 7L};
        when(stockMovementRepository.findTopMovedProductsSince(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(row));

        List<TopProductDTO> result = dashboardService.getTopMovedProducts();

        assertThat(result).containsExactly(new TopProductDTO(productId, "LAP-001", "Laptop", 7L));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(stockMovementRepository)
                .findTopMovedProductsSince(any(LocalDateTime.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    private ProductResponseDTO buildProduct(String name, String sku) {
        return new ProductResponseDTO(UUID.randomUUID(), name, sku, null, "Electronica",
                new BigDecimal("999.99"), 2, 5, ProductStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now(), "admin@test.com", 0L);
    }

    private StockMovementResponseDTO buildMovement() {
        return new StockMovementResponseDTO(UUID.randomUUID(), UUID.randomUUID(), "LAP-001", "Laptop",
                MovementType.ENTRY, 5, 10, 5, "Reabastecimiento", null, "admin@test.com", LocalDateTime.now());
    }
}
