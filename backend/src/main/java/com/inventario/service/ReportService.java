package com.inventario.service;

import com.inventario.dto.InventoryReportDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Compone los datos del dashboard en un unico reporte de inventario (GET /api/reports/inventory). */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final DashboardService dashboardService;

    public ReportService(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    public InventoryReportDTO getInventoryReport() {
        return new InventoryReportDTO(
                LocalDateTime.now(),
                dashboardService.getSummary(),
                dashboardService.getCriticalProducts(),
                dashboardService.getRecentMovements(),
                dashboardService.getTopMovedProducts());
    }
}
