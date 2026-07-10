package com.inventario.unit;

import com.inventario.config.SecurityConfig;
import com.inventario.controller.DashboardController;
import com.inventario.dto.DashboardSummaryDTO;
import com.inventario.dto.ProductResponseDTO;
import com.inventario.dto.StockMovementResponseDTO;
import com.inventario.dto.TopProductDTO;
import com.inventario.entity.MovementType;
import com.inventario.entity.ProductStatus;
import com.inventario.security.JwtAuthConverter;
import com.inventario.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class})
class DashboardControllerTest {

    private static final String REPORT_VIEW_SCOPE = "SCOPE_report:view";
    private static final String INSUFFICIENT_SCOPE = "SCOPE_product:manage";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    void getSummary_withReportViewScope_returns200() throws Exception {
        given(dashboardService.getSummary()).willReturn(
                new DashboardSummaryDTO(10, 8, 2, 3, new BigDecimal("1500.00"), 42));

        mockMvc.perform(get("/api/dashboard/summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority(REPORT_VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(10))
                .andExpect(jsonPath("$.totalStockMovements").value(42));
    }

    @Test
    void getSummary_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSummary_withoutReportViewScope_returns403() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority(INSUFFICIENT_SCOPE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCriticalProducts_withReportViewScope_returns200() throws Exception {
        given(dashboardService.getCriticalProducts()).willReturn(List.of(buildProduct()));

        mockMvc.perform(get("/api/dashboard/critical-products")
                        .with(jwt().authorities(new SimpleGrantedAuthority(REPORT_VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("LAP-001"));
    }

    @Test
    void getCriticalProducts_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/critical-products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRecentMovements_withReportViewScope_returns200() throws Exception {
        given(dashboardService.getRecentMovements()).willReturn(List.of(buildMovement()));

        mockMvc.perform(get("/api/dashboard/recent-movements")
                        .with(jwt().authorities(new SimpleGrantedAuthority(REPORT_VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productSku").value("LAP-001"));
    }

    @Test
    void getRecentMovements_withoutReportViewScope_returns403() throws Exception {
        mockMvc.perform(get("/api/dashboard/recent-movements")
                        .with(jwt().authorities(new SimpleGrantedAuthority(INSUFFICIENT_SCOPE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTopProducts_withReportViewScope_returns200() throws Exception {
        given(dashboardService.getTopMovedProducts()).willReturn(
                List.of(new TopProductDTO(UUID.randomUUID(), "LAP-001", "Laptop", 7L)));

        mockMvc.perform(get("/api/dashboard/top-products")
                        .with(jwt().authorities(new SimpleGrantedAuthority(REPORT_VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movementCount").value(7));
    }

    @Test
    void getTopProducts_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/top-products"))
                .andExpect(status().isUnauthorized());
    }

    private ProductResponseDTO buildProduct() {
        return new ProductResponseDTO(UUID.randomUUID(), "Laptop", "LAP-001", null, "Electronica",
                new BigDecimal("999.99"), 2, 5, ProductStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now(), "admin@test.com", 0L);
    }

    private StockMovementResponseDTO buildMovement() {
        return new StockMovementResponseDTO(UUID.randomUUID(), UUID.randomUUID(), "LAP-001", "Laptop",
                MovementType.ENTRY, 5, 10, 5, "Reabastecimiento", null, "admin@test.com", LocalDateTime.now());
    }
}
