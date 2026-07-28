package com.inventario.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.config.SecurityConfig;
import com.inventario.controller.ProductController;
import com.inventario.dto.ProductRequestDTO;
import com.inventario.dto.ProductResponseDTO;
import com.inventario.dto.ProductStatsDTO;
import com.inventario.entity.ProductStatus;
import com.inventario.exception.DuplicateSkuException;
import com.inventario.exception.ProductNotFoundException;
import com.inventario.security.JwtAuthConverter;
import com.inventario.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class})
class ProductControllerTest {

    private static final String VIEW_SCOPE = "SCOPE_product:view";
    private static final String MANAGE_SCOPE = "SCOPE_product:manage";
    private static final String REPORT_VIEW_SCOPE = "SCOPE_report:view";
    private static final String AUDIT_VIEW_SCOPE = "SCOPE_audit:view";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    void getAllProducts_withViewScope_returns200AndPagedResult() throws Exception {
        given(productService.getAllProducts(any(Pageable.class), any()))
                .willReturn(new PageImpl<>(List.of(buildResponse(UUID.randomUUID(), "LAP-001"))));

        mockMvc.perform(get("/api/products").with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("LAP-001"));
    }

    @Test
    void getAllProducts_withFilters_passesThemToService() throws Exception {
        given(productService.getAllProducts(any(Pageable.class), any()))
                .willReturn(new PageImpl<>(List.of(buildResponse(UUID.randomUUID(), "LAP-001"))));

        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "5")
                        .param("category", "Electronica")
                        .param("status", "ACTIVE")
                        .param("minPrice", "10.00")
                        .param("maxPrice", "1000.00")
                        .param("q", "laptop")
                        .with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE))))
                .andExpect(status().isOk());
    }

    @Test
    void getAllProducts_withInvalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("status", "NO_EXISTE")
                        .with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllProducts_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllProducts_withoutViewScope_returns403() throws Exception {
        mockMvc.perform(get("/api/products").with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllProducts_withOnlyAuditViewScope_returns200() throws Exception {
        // SEC-008: AUDITOR (audit:view, sin product:view) necesita poder listar productos
        // para elegir cual auditar en AuditPage.jsx - ver el comentario en el controller.
        given(productService.getAllProducts(any(Pageable.class), any()))
                .willReturn(new PageImpl<>(List.of(buildResponse(UUID.randomUUID(), "LAP-001"))));

        mockMvc.perform(get("/api/products").with(jwt().authorities(new SimpleGrantedAuthority(AUDIT_VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("LAP-001"));
    }

    @Test
    void searchProducts_withViewScope_returns200AndResults() throws Exception {
        given(productService.searchProducts(eq("laptop"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(buildResponse(UUID.randomUUID(), "LAP-001"))));

        mockMvc.perform(get("/api/products/search").param("q", "laptop")
                        .with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("LAP-001"));
    }

    @Test
    void searchProducts_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/products/search").param("q", "laptop"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchProducts_withoutViewScope_returns403() throws Exception {
        mockMvc.perform(get("/api/products/search").param("q", "laptop")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCriticalProducts_withViewScope_returns200() throws Exception {
        given(productService.getProductsBelowMinStock())
                .willReturn(List.of(buildResponse(UUID.randomUUID(), "LOW-001")));

        mockMvc.perform(get("/api/products/critical").with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("LOW-001"));
    }

    @Test
    void getCriticalProducts_withManageScope_returns200() throws Exception {
        given(productService.getProductsBelowMinStock())
                .willReturn(List.of(buildResponse(UUID.randomUUID(), "LOW-001")));

        mockMvc.perform(get("/api/products/critical").with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE))))
                .andExpect(status().isOk());
    }

    @Test
    void getCriticalProducts_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/products/critical"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCriticalProducts_withUnrelatedScope_returns403() throws Exception {
        mockMvc.perform(get("/api/products/critical").with(jwt().authorities(new SimpleGrantedAuthority(REPORT_VIEW_SCOPE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProductStats_withReportViewScope_returns200() throws Exception {
        given(productService.getProductStats())
                .willReturn(new ProductStatsDTO(10, 8, 2, 3, new BigDecimal("1500.00")));

        mockMvc.perform(get("/api/products/stats").with(jwt().authorities(new SimpleGrantedAuthority(REPORT_VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(10))
                .andExpect(jsonPath("$.belowMinStockProducts").value(3));
    }

    @Test
    void getProductStats_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/products/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProductStats_withProductViewScope_returns403() throws Exception {
        mockMvc.perform(get("/api/products/stats").with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProductById_withExistingId_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(productService.getProductById(id)).willReturn(buildResponse(id, "LAP-001"));

        mockMvc.perform(get("/api/products/{id}", id).with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getProductById_withNonExistingId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(productService.getProductById(id)).willThrow(new ProductNotFoundException(id));

        mockMvc.perform(get("/api/products/{id}", id).with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProductById_withOnlyAuditViewScope_returns200() throws Exception {
        // SEC-008: AUDITOR llega aca haciendo click en un producto desde el Dashboard
        // (widgets con report:view) o desde AuditPage - ver el comentario en el controller.
        UUID id = UUID.randomUUID();
        given(productService.getProductById(id)).willReturn(buildResponse(id, "LAP-001"));

        mockMvc.perform(get("/api/products/{id}", id).with(jwt().authorities(new SimpleGrantedAuthority(AUDIT_VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getProductById_withoutAnyViewScope_returns403() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/products/{id}", id).with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_withValidData_returns201() throws Exception {
        ProductRequestDTO request = buildRequest("LAP-001");
        given(productService.createProduct(any())).willReturn(buildResponse(UUID.randomUUID(), "LAP-001"));

        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("LAP-001"));
    }

    @Test
    void createProduct_withInvalidData_returns400() throws Exception {
        ProductRequestDTO invalidRequest = new ProductRequestDTO(
                "", "LAP-001", "Laptop 15 pulgadas", "Electronica", BigDecimal.ZERO, -1, -1);

        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_withDuplicateSku_returns409() throws Exception {
        ProductRequestDTO request = buildRequest("LAP-001");
        given(productService.createProduct(any())).willThrow(new DuplicateSkuException("LAP-001"));

        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void createProduct_withoutManageScope_returns403() throws Exception {
        ProductRequestDTO request = buildRequest("LAP-001");

        mockMvc.perform(post("/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProduct_withValidData_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        ProductRequestDTO request = buildRequest("LAP-001");
        given(productService.updateProduct(eq(id), any())).willReturn(buildResponse(id, "LAP-001"));

        mockMvc.perform(put("/api/products/{id}", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void updateProduct_withNonExistingId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        ProductRequestDTO request = buildRequest("LAP-001");
        given(productService.updateProduct(eq(id), any())).willThrow(new ProductNotFoundException(id));

        mockMvc.perform(put("/api/products/{id}", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProduct_withExistingId_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/products/{id}", id).with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProduct_withNonExistingId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ProductNotFoundException(id)).when(productService).deleteProduct(id);

        mockMvc.perform(delete("/api/products/{id}", id).with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE))))
                .andExpect(status().isNotFound());
    }

    private ProductRequestDTO buildRequest(String sku) {
        return new ProductRequestDTO("Laptop", sku, "Laptop 15 pulgadas", "Electronica", new BigDecimal("999.99"), 10, 2);
    }

    private ProductResponseDTO buildResponse(UUID id, String sku) {
        return new ProductResponseDTO(id, "Laptop", sku, "Laptop 15 pulgadas", "Electronica",
                new BigDecimal("999.99"), 10, 2, ProductStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(),
                "admin", 0L);
    }
}
