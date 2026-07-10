package com.inventario.controller;

import com.inventario.dto.InventoryReportDTO;
import com.inventario.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reportes", description = "Reportes agregados del inventario")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('SCOPE_report:view')")
    @Operation(summary = "Reporte completo de inventario (resumen, criticos, movimientos recientes y mas movidos)")
    @ApiResponse(responseCode = "200", description = "Reporte de inventario")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public InventoryReportDTO getInventoryReport() {
        return reportService.getInventoryReport();
    }
}
