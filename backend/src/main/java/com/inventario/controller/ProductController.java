package com.inventario.controller;

import com.inventario.dto.ProductFilterDTO;
import com.inventario.dto.ProductRequestDTO;
import com.inventario.dto.ProductResponseDTO;
import com.inventario.dto.ProductStatsDTO;
import com.inventario.entity.ProductStatus;
import com.inventario.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Gestion de productos del inventario")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_product:view')")
    @Operation(summary = "Listar productos paginados, con filtros dinamicos opcionales")
    @ApiResponse(responseCode = "200", description = "Pagina de productos")
    @ApiResponse(responseCode = "400", description = "Parametro invalido (status/precio/orden)", content = @Content)
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public Page<ProductResponseDTO> getAllProducts(
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable,
            @Parameter(description = "Categoria exacta (ignora mayusculas/minusculas)")
            @RequestParam(required = false) String category,
            @Parameter(description = "Status del producto; por defecto ACTIVE si no se especifica")
            @RequestParam(required = false) ProductStatus status,
            @Parameter(description = "Precio minimo (inclusive)")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Precio maximo (inclusive)")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Texto libre: busca en nombre, SKU y categoria")
            @RequestParam(required = false) String q) {
        return productService.getAllProducts(pageable, new ProductFilterDTO(status, category, minPrice, maxPrice, q));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('SCOPE_product:view')")
    @Operation(summary = "Buscar productos por texto libre (nombre, SKU o categoria)")
    @ApiResponse(responseCode = "200", description = "Pagina de productos que coinciden con la busqueda")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public Page<ProductResponseDTO> searchProducts(
            @Parameter(description = "Texto a buscar en nombre, SKU o categoria")
            @RequestParam String q,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return productService.searchProducts(q, pageable);
    }

    @GetMapping("/critical")
    @PreAuthorize("hasAnyAuthority('SCOPE_product:view', 'SCOPE_product:manage')")
    @Operation(summary = "Listar productos activos bajo su stock minimo")
    @ApiResponse(responseCode = "200", description = "Lista de productos criticos")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public List<ProductResponseDTO> getCriticalProducts() {
        return productService.getProductsBelowMinStock();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('SCOPE_report:view')")
    @Operation(summary = "Estadisticas agregadas del inventario de productos")
    @ApiResponse(responseCode = "200", description = "Estadisticas de productos")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    public ProductStatsDTO getProductStats() {
        return productService.getProductStats();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_product:view')")
    @Operation(summary = "Obtener un producto por su id")
    @ApiResponse(responseCode = "200", description = "Producto encontrado")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    public ProductResponseDTO getProductById(@PathVariable UUID id) {
        return productService.getProductById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_product:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un producto")
    @ApiResponse(responseCode = "201", description = "Producto creado")
    @ApiResponse(responseCode = "400", description = "Datos invalidos", content = @Content)
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    @ApiResponse(responseCode = "409", description = "SKU duplicado", content = @Content)
    public ProductResponseDTO createProduct(@Valid @RequestBody ProductRequestDTO request) {
        return productService.createProduct(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_product:manage')")
    @Operation(summary = "Actualizar un producto")
    @ApiResponse(responseCode = "200", description = "Producto actualizado")
    @ApiResponse(responseCode = "400", description = "Datos invalidos", content = @Content)
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    @ApiResponse(responseCode = "409", description = "SKU duplicado", content = @Content)
    public ProductResponseDTO updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequestDTO request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_product:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar (soft delete) un producto")
    @ApiResponse(responseCode = "204", description = "Producto eliminado")
    @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)", content = @Content)
    @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content)
    public void deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
    }
}
