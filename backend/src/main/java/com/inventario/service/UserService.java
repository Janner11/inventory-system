package com.inventario.service;

import com.inventario.dto.UserRequestDTO;
import com.inventario.dto.UserResponseDTO;
import com.inventario.security.KeycloakAdminClient;
import com.inventario.security.KeycloakAdminClient.KeycloakRoleRepresentation;
import com.inventario.security.KeycloakAdminClient.KeycloakUserRepresentation;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Completa el scope {@code user:manage} (sección 6 de CLAUDE.md), delegando en
 * {@link KeycloakAdminClient} — este servicio no toca la base de datos del backend: los
 * usuarios viven exclusivamente en Keycloak (ADR implícito de SEC-001, usuarios
 * gestionados por un administrador, no auto-registro — {@code registrationAllowed: false}
 * en {@code realm.json}).
 */
@Service
public class UserService {

    /** Los 5 roles de la sección 6 de CLAUDE.md — únicos asignables desde este panel. */
    public static final List<String> ASSIGNABLE_ROLES = List.of("ADMIN", "MANAGER", "WAREHOUSE", "VIEWER", "AUDITOR");

    private static final String SERVICE_ACCOUNT_PREFIX = "service-account-";

    private final KeycloakAdminClient keycloakAdminClient;

    public UserService(KeycloakAdminClient keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    /**
     * Lista todos los usuarios humanos del realm (excluye el service account de
     * {@code inventario-backend}) con su rol resuelto. Como Keycloak no incluye el rol en el
     * listado de usuarios, se resuelve con una consulta de role-mappings por usuario
     * ({@code GET .../users/{userId}/role-mappings/realm}) — ver el comentario de
     * {@link KeycloakAdminClient#getUserRealmRoles(String)} sobre por qué no se usa el
     * endpoint por-rol ({@code .../roles/{role}/users}, que el service account no puede
     * usar pese a tener los permisos correctos).
     */
    public List<UserResponseDTO> getAllUsers() {
        return keycloakAdminClient.listUsers().stream()
                .filter(user -> user.username() == null || !user.username().startsWith(SERVICE_ACCOUNT_PREFIX))
                .map(user -> toResponseDTO(user, resolveAssignableRole(user.id())))
                .sorted(Comparator.comparing(UserResponseDTO::username))
                .toList();
    }

    private String resolveAssignableRole(String userId) {
        return keycloakAdminClient.getUserRealmRoles(userId).stream()
                .map(KeycloakRoleRepresentation::name)
                .filter(ASSIGNABLE_ROLES::contains)
                .findFirst()
                .orElse(null);
    }

    public UserResponseDTO createUser(UserRequestDTO request) {
        validateRole(request.role());

        String userId = keycloakAdminClient.createUser(
                request.email(), request.email(), request.firstName(), request.lastName(), request.password());
        keycloakAdminClient.assignRealmRole(userId, request.role());

        return new UserResponseDTO(
                userId, request.email(), request.email(), request.firstName(), request.lastName(), true, request.role());
    }

    /** Habilita/deshabilita la cuenta — equivalente al soft delete de ADR-001, aplicado a usuarios. */
    public void setUserEnabled(String userId, boolean enabled) {
        keycloakAdminClient.setEnabled(userId, enabled);
    }

    private void validateRole(String role) {
        if (!ASSIGNABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("role: debe ser uno de " + ASSIGNABLE_ROLES);
        }
    }

    private UserResponseDTO toResponseDTO(KeycloakUserRepresentation user, String role) {
        return new UserResponseDTO(
                user.id(), user.username(), user.email(), user.firstName(), user.lastName(), user.enabled(), role);
    }
}
