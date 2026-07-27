package com.inventario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear un usuario vía la Keycloak Admin REST API (SEC-007). El email se usa
 * también como username (mismo criterio que los 5 usuarios de prueba de {@code realm.json},
 * sección 6 de CLAUDE.md: {@code loginWithEmailAllowed: true}). {@code role} debe ser uno de
 * los 5 roles asignables (ver {@code GET /api/users/roles}).
 */
public record UserRequestDTO(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotBlank
        String role
) {
}
