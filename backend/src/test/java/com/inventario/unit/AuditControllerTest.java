package com.inventario.unit;

import com.inventario.config.SecurityConfig;
import com.inventario.controller.AuditController;
import com.inventario.dto.ProductRevisionDTO;
import com.inventario.entity.ProductStatus;
import com.inventario.exception.ProductNotFoundException;
import com.inventario.security.JwtAuthConverter;
import com.inventario.service.AuditService;
import org.hibernate.envers.RevisionType;
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

@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class})
class AuditControllerTest {

    private static final String VIEW_SCOPE = "SCOPE_product:view";
    private static final String MANAGE_SCOPE = "SCOPE_product:manage";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    @Test
    void getProductRevisions_withViewScope_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(auditService.getProductRevisions(id)).willReturn(List.of(
                buildRevision(id, 1, RevisionType.ADD, 10),
                buildRevision(id, 2, RevisionType.MOD, 8)
        ));

        mockMvc.perform(get("/api/audit/products/{id}/revisions", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revisionType").value("ADD"))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[1].revisionType").value("MOD"))
                .andExpect(jsonPath("$[1].quantity").value(8));
    }

    @Test
    void getProductRevisions_withoutToken_returns401() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/audit/products/{id}/revisions", id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProductRevisions_withoutViewScope_returns403() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/audit/products/{id}/revisions", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProductRevisions_withNonExistingProduct_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(auditService.getProductRevisions(id)).willThrow(new ProductNotFoundException(id));

        mockMvc.perform(get("/api/audit/products/{id}/revisions", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority(VIEW_SCOPE))))
                .andExpect(status().isNotFound());
    }

    private ProductRevisionDTO buildRevision(UUID id, int revisionNumber, RevisionType type, int quantity) {
        return new ProductRevisionDTO(
                revisionNumber,
                LocalDateTime.now(),
                type,
                id,
                "Laptop",
                "LAP-001",
                "Laptop 15 pulgadas",
                "Electronica",
                new BigDecimal("999.99"),
                quantity,
                2,
                ProductStatus.ACTIVE
        );
    }
}
