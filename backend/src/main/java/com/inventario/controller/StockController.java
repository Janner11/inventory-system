package com.inventario.controller;

import com.inventario.dto.StockMovementRequestDTO;
import com.inventario.dto.StockMovementResponseDTO;
import com.inventario.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/stock")
@Tag(name = "Stock", description = "Operaciones de control de stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('SCOPE_stock:view')")
    @Operation(summary = "Historial global de movimientos de stock, paginado")
    @ApiResponse(responseCode = "200", description = "Pagina de movimientos")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public Page<StockMovementResponseDTO> getRecentMovements(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return stockService.getRecentMovements(pageable);
    }

    @GetMapping("/movements/{productId}")
    @PreAuthorize("hasAuthority('SCOPE_stock:view')")
    @Operation(summary = "Historial de movimientos de stock de un producto")
    @ApiResponse(responseCode = "200", description = "Pagina de movimientos del producto")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    public Page<StockMovementResponseDTO> getMovementsByProduct(
            @PathVariable UUID productId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return stockService.getMovementsByProduct(productId, pageable);
    }

    @PostMapping("/entry")
    @PreAuthorize("hasAuthority('SCOPE_stock:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar entrada de stock")
    @ApiResponse(responseCode = "201", description = "Entrada registrada")
    @ApiResponse(responseCode = "400", description = "Datos invalidos", content = @Content)
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    @ApiResponse(responseCode = "409", description = "Producto inactivo", content = @Content)
    public StockMovementResponseDTO registerEntry(@Valid @RequestBody StockMovementRequestDTO request) {
        return stockService.registerEntry(request);
    }

    @PostMapping("/exit")
    @PreAuthorize("hasAuthority('SCOPE_stock:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar salida de stock")
    @ApiResponse(responseCode = "201", description = "Salida registrada")
    @ApiResponse(responseCode = "400", description = "Datos invalidos", content = @Content)
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    @ApiResponse(responseCode = "409", description = "Producto inactivo", content = @Content)
    @ApiResponse(responseCode = "422", description = "Stock insuficiente", content = @Content)
    public StockMovementResponseDTO registerExit(@Valid @RequestBody StockMovementRequestDTO request) {
        return stockService.registerExit(request);
    }
}
