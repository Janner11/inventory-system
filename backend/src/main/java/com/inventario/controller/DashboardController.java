package com.inventario.controller;

import com.inventario.dto.DashboardSummaryDTO;
import com.inventario.dto.ProductResponseDTO;
import com.inventario.dto.StockMovementResponseDTO;
import com.inventario.dto.TopProductDTO;
import com.inventario.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "KPIs y widgets del panel principal")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('SCOPE_report:view')")
    @Operation(summary = "KPIs principales del inventario")
    @ApiResponse(responseCode = "200", description = "Resumen de KPIs")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public DashboardSummaryDTO getSummary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/critical-products")
    @PreAuthorize("hasAuthority('SCOPE_report:view')")
    @Operation(summary = "Productos activos en alerta de stock bajo")
    @ApiResponse(responseCode = "200", description = "Lista de productos en alerta")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public List<ProductResponseDTO> getCriticalProducts() {
        return dashboardService.getCriticalProducts();
    }

    @GetMapping("/recent-movements")
    @PreAuthorize("hasAuthority('SCOPE_report:view')")
    @Operation(summary = "Ultimos 10 movimientos de stock, mas recientes primero")
    @ApiResponse(responseCode = "200", description = "Lista de movimientos recientes")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public List<StockMovementResponseDTO> getRecentMovements() {
        return dashboardService.getRecentMovements();
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasAuthority('SCOPE_report:view')")
    @Operation(summary = "Productos con mas movimientos de stock en los ultimos 30 dias")
    @ApiResponse(responseCode = "200", description = "Lista de productos mas movidos")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public List<TopProductDTO> getTopProducts() {
        return dashboardService.getTopMovedProducts();
    }
}
