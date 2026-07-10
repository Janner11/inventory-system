package com.inventario.controller;

import com.inventario.dto.StockMovementRequestDTO;
import com.inventario.dto.StockMovementResponseDTO;
import com.inventario.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock")
@Tag(name = "Stock", description = "Operaciones de control de stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/entry")
    @PreAuthorize("hasAuthority('SCOPE_product:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar entrada de stock")
    @ApiResponse(responseCode = "201", description = "Entrada registrada")
    @ApiResponse(responseCode = "400", description = "Datos invalidos", content = @Content)
    @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    public StockMovementResponseDTO registerEntry(@Valid @RequestBody StockMovementRequestDTO request) {
        return stockService.registerEntry(request);
    }

    @PostMapping("/exit")
    @PreAuthorize("hasAuthority('SCOPE_product:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar salida de stock")
    @ApiResponse(responseCode = "201", description = "Salida registrada")
    @ApiResponse(responseCode = "400", description = "Datos invalidos", content = @Content)
    @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    @ApiResponse(responseCode = "422", description = "Stock insuficiente", content = @Content)
    public StockMovementResponseDTO registerExit(@Valid @RequestBody StockMovementRequestDTO request) {
        return stockService.registerExit(request);
    }
}
