package com.inventario.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.config.SecurityConfig;
import com.inventario.controller.StockController;
import com.inventario.dto.StockMovementRequestDTO;
import com.inventario.dto.StockMovementResponseDTO;
import com.inventario.entity.MovementType;
import com.inventario.exception.InsufficientStockException;
import com.inventario.exception.ProductNotFoundException;
import com.inventario.security.JwtAuthConverter;
import com.inventario.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class})
class StockControllerTest {

    private static final String VIEW_SCOPE = "SCOPE_product:view";
    private static final String MANAGE_SCOPE = "SCOPE_product:manage";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StockService stockService;

    @Test
    void registerEntry_withManageScope_returns201() throws Exception {
        StockMovementRequestDTO request = buildRequest(5);
        given(stockService.registerEntry(any())).willReturn(buildResponse(MovementType.ENTRY, 10, 15, 5));

        mockMvc.perform(post("/api/stock/entry")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ENTRY"))
                .andExpect(jsonPath("$.newQuantity").value(15));
    }

    @Test
    void registerEntry_withoutToken_returns401() throws Exception {
        StockMovementRequestDTO request = buildRequest(5);

        mockMvc.perform(post("/api/stock/entry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerEntry_withoutManageScope_returns403() throws Exception {
        StockMovementRequestDTO request = buildRequest(5);

        mockMvc.perform(post("/api/stock/entry")
                        .with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerEntry_withInvalidData_returns400() throws Exception {
        StockMovementRequestDTO invalidRequest = new StockMovementRequestDTO(null, 0, "Reabastecimiento", null, "admin");

        mockMvc.perform(post("/api/stock/entry")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerEntry_withNonExistingProduct_returns404() throws Exception {
        StockMovementRequestDTO request = buildRequest(5);
        given(stockService.registerEntry(any())).willThrow(new ProductNotFoundException(request.productId()));

        mockMvc.perform(post("/api/stock/entry")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void registerExit_withManageScope_returns201() throws Exception {
        StockMovementRequestDTO request = buildRequest(4);
        given(stockService.registerExit(any())).willReturn(buildResponse(MovementType.EXIT, 10, 6, -4));

        mockMvc.perform(post("/api/stock/exit")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("EXIT"))
                .andExpect(jsonPath("$.newQuantity").value(6));
    }

    @Test
    void registerExit_withoutToken_returns401() throws Exception {
        StockMovementRequestDTO request = buildRequest(4);

        mockMvc.perform(post("/api/stock/exit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerExit_withoutManageScope_returns403() throws Exception {
        StockMovementRequestDTO request = buildRequest(4);

        mockMvc.perform(post("/api/stock/exit")
                        .with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerExit_withInvalidData_returns400() throws Exception {
        StockMovementRequestDTO invalidRequest = new StockMovementRequestDTO(null, -1, "Venta", null, "admin");

        mockMvc.perform(post("/api/stock/exit")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerExit_withInsufficientStock_returns422() throws Exception {
        StockMovementRequestDTO request = buildRequest(100);
        given(stockService.registerExit(any())).willThrow(new InsufficientStockException("LAP-001", 10, 100));

        mockMvc.perform(post("/api/stock/exit")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void registerExit_withNonExistingProduct_returns404() throws Exception {
        StockMovementRequestDTO request = buildRequest(4);
        given(stockService.registerExit(any())).willThrow(new ProductNotFoundException(request.productId()));

        mockMvc.perform(post("/api/stock/exit")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    private StockMovementRequestDTO buildRequest(int quantity) {
        return new StockMovementRequestDTO(UUID.randomUUID(), quantity, "Movimiento de prueba", null, "admin");
    }

    private StockMovementResponseDTO buildResponse(MovementType type, int previousQuantity, int newQuantity, int quantity) {
        return new StockMovementResponseDTO(UUID.randomUUID(), UUID.randomUUID(), "LAP-001", "Laptop",
                type, previousQuantity, newQuantity, quantity, "Movimiento de prueba", null, "admin", null);
    }
}
