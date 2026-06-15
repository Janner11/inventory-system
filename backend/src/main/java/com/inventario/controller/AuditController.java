package com.inventario.controller;

import com.inventario.dto.ProductRevisionDTO;
import com.inventario.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Auditoria", description = "Consulta del historial de auditoria (Hibernate Envers)")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/products/{id}/revisions")
    @PreAuthorize("hasAuthority('SCOPE_product:view')")
    @Operation(summary = "Historial de revisiones (auditoria) de un producto")
    @ApiResponse(responseCode = "200", description = "Historial de revisiones")
    @ApiResponse(responseCode = "404", description = "Producto sin historial de auditoria", content = @Content)
    public List<ProductRevisionDTO> getProductRevisions(@PathVariable UUID id) {
        return auditService.getProductRevisions(id);
    }
}
