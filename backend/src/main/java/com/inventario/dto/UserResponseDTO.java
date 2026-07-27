package com.inventario.dto;

public record UserResponseDTO(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        String role
) {
}
