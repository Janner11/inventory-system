package com.inventario.unit;

import com.inventario.config.SecurityConfig;
import com.inventario.controller.UserController;
import com.inventario.dto.UserRequestDTO;
import com.inventario.dto.UserResponseDTO;
import com.inventario.exception.UserAlreadyExistsException;
import com.inventario.exception.UserNotFoundException;
import com.inventario.security.JwtAuthConverter;
import com.inventario.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class})
class UserControllerTest {

    private static final String MANAGE_SCOPE = "SCOPE_user:manage";
    private static final String INSUFFICIENT_SCOPE = "SCOPE_product:view";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    void getAllUsers_withManageScope_returns200() throws Exception {
        given(userService.getAllUsers()).willReturn(List.of(
                new UserResponseDTO("id-1", "admin@test.com", "admin@test.com", "Admin", "Test", true, "ADMIN")));

        mockMvc.perform(get("/api/users").with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin@test.com"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void getAllUsers_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void getAllUsers_withoutManageScope_returns403() throws Exception {
        mockMvc.perform(get("/api/users").with(jwt().authorities(new SimpleGrantedAuthority(INSUFFICIENT_SCOPE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAssignableRoles_withManageScope_returns200() throws Exception {
        mockMvc.perform(get("/api/users/roles").with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("ADMIN"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void createUser_withValidDataAndManageScope_returns201() throws Exception {
        UserRequestDTO request = new UserRequestDTO("nuevo@test.com", "Nuevo", "Usuario", "password123", "VIEWER");
        given(userService.createUser(any())).willReturn(
                new UserResponseDTO("id-2", "nuevo@test.com", "nuevo@test.com", "Nuevo", "Usuario", true, "VIEWER"));

        mockMvc.perform(post("/api/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("nuevo@test.com"))
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    void createUser_withInvalidData_returns400() throws Exception {
        UserRequestDTO request = new UserRequestDTO("no-es-un-email", "", "Usuario", "1234567", "VIEWER");

        mockMvc.perform(post("/api/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_withDuplicateEmail_returns409() throws Exception {
        UserRequestDTO request = new UserRequestDTO("admin@test.com", "Admin", "Test", "password123", "ADMIN");
        given(userService.createUser(any())).willThrow(new UserAlreadyExistsException("admin@test.com"));

        mockMvc.perform(post("/api/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void createUser_withoutManageScope_returns403() throws Exception {
        UserRequestDTO request = new UserRequestDTO("nuevo@test.com", "Nuevo", "Usuario", "password123", "VIEWER");

        mockMvc.perform(post("/api/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority(INSUFFICIENT_SCOPE)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void setUserEnabled_withManageScope_returns204() throws Exception {
        mockMvc.perform(patch("/api/users/{id}/status", "id-1")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType("application/json")
                        .content("{\"enabled\": false}"))
                .andExpect(status().isNoContent());

        then(userService).should().setUserEnabled(eq("id-1"), eq(false));
    }

    @Test
    void setUserEnabled_withNonExistingUser_returns404() throws Exception {
        org.mockito.BDDMockito.willThrow(new UserNotFoundException("id-404"))
                .given(userService).setUserEnabled("id-404", true);

        mockMvc.perform(patch("/api/users/{id}/status", "id-404")
                        .with(jwt().authorities(new SimpleGrantedAuthority(MANAGE_SCOPE)))
                        .contentType("application/json")
                        .content("{\"enabled\": true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void setUserEnabled_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/users/{id}/status", "id-1")
                        .contentType("application/json")
                        .content("{\"enabled\": false}"))
                .andExpect(status().isUnauthorized());
    }
}
