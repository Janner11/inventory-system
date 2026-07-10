package com.inventario.unit;

import com.inventario.config.SecurityConfig;
import com.inventario.controller.ReportController;
import com.inventario.dto.DashboardSummaryDTO;
import com.inventario.dto.InventoryReportDTO;
import com.inventario.security.JwtAuthConverter;
import com.inventario.service.ReportService;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class})
class ReportControllerTest {

    private static final String REPORT_VIEW_SCOPE = "SCOPE_report:view";
    private static final String INSUFFICIENT_SCOPE = "SCOPE_product:manage";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @Test
    void getInventoryReport_withReportViewScope_returns200() throws Exception {
        DashboardSummaryDTO summary = new DashboardSummaryDTO(10, 8, 2, 3, new BigDecimal("1500.00"), 42);
        given(reportService.getInventoryReport()).willReturn(
                new InventoryReportDTO(LocalDateTime.now(), summary, List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/reports/inventory")
                        .with(jwt().authorities(new SimpleGrantedAuthority(REPORT_VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalProducts").value(10))
                .andExpect(jsonPath("$.criticalProducts").isArray())
                .andExpect(jsonPath("$.recentMovements").isArray())
                .andExpect(jsonPath("$.topProducts").isArray());
    }

    @Test
    void getInventoryReport_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/reports/inventory"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getInventoryReport_withoutReportViewScope_returns403() throws Exception {
        mockMvc.perform(get("/api/reports/inventory")
                        .with(jwt().authorities(new SimpleGrantedAuthority(INSUFFICIENT_SCOPE))))
                .andExpect(status().isForbidden());
    }
}
