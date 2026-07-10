package com.inventario.unit;

import com.inventario.dto.DashboardSummaryDTO;
import com.inventario.dto.InventoryReportDTO;
import com.inventario.dto.ProductResponseDTO;
import com.inventario.dto.StockMovementResponseDTO;
import com.inventario.dto.TopProductDTO;
import com.inventario.service.DashboardService;
import com.inventario.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private DashboardService dashboardService;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(dashboardService);
    }

    @Test
    void getInventoryReport_composesAllDashboardData() {
        DashboardSummaryDTO summary = new DashboardSummaryDTO(10, 8, 2, 1, new BigDecimal("500.00"), 20);
        List<ProductResponseDTO> criticalProducts = List.of();
        List<StockMovementResponseDTO> recentMovements = List.of();
        List<TopProductDTO> topProducts = List.of();

        when(dashboardService.getSummary()).thenReturn(summary);
        when(dashboardService.getCriticalProducts()).thenReturn(criticalProducts);
        when(dashboardService.getRecentMovements()).thenReturn(recentMovements);
        when(dashboardService.getTopMovedProducts()).thenReturn(topProducts);

        InventoryReportDTO report = reportService.getInventoryReport();

        assertThat(report.generatedAt()).isNotNull();
        assertThat(report.summary()).isEqualTo(summary);
        assertThat(report.criticalProducts()).isEqualTo(criticalProducts);
        assertThat(report.recentMovements()).isEqualTo(recentMovements);
        assertThat(report.topProducts()).isEqualTo(topProducts);
    }
}
