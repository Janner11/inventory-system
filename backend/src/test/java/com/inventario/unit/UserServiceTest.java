package com.inventario.unit;

import com.inventario.dto.UserRequestDTO;
import com.inventario.dto.UserResponseDTO;
import com.inventario.security.KeycloakAdminClient;
import com.inventario.security.KeycloakAdminClient.KeycloakRoleRepresentation;
import com.inventario.security.KeycloakAdminClient.KeycloakUserRepresentation;
import com.inventario.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(keycloakAdminClient);
    }

    @Test
    void getAllUsers_resolvesRolePerUser_andExcludesServiceAccount() {
        KeycloakUserRepresentation admin = new KeycloakUserRepresentation("id-admin", "admin@test.com", "admin@test.com", "Admin", "Test", true);
        KeycloakUserRepresentation viewer = new KeycloakUserRepresentation("id-viewer", "viewer@test.com", "viewer@test.com", "Viewer", "Test", true);
        KeycloakUserRepresentation serviceAccount = new KeycloakUserRepresentation("id-svc", "service-account-inventario-backend", null, null, null, true);

        given(keycloakAdminClient.listUsers()).willReturn(List.of(admin, viewer, serviceAccount));
        given(keycloakAdminClient.getUserRealmRoles("id-admin"))
                .willReturn(List.of(new KeycloakRoleRepresentation("role-1", "ADMIN")));
        given(keycloakAdminClient.getUserRealmRoles("id-viewer"))
                .willReturn(List.of(new KeycloakRoleRepresentation("role-2", "VIEWER"), new KeycloakRoleRepresentation("role-3", "offline_access")));

        List<UserResponseDTO> result = userService.getAllUsers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserResponseDTO::username).containsExactly("admin@test.com", "viewer@test.com");
        assertThat(result.get(0).role()).isEqualTo("ADMIN");
        assertThat(result.get(1).role()).isEqualTo("VIEWER");
        then(keycloakAdminClient).should(never()).getUserRealmRoles("id-svc");
    }

    @Test
    void createUser_withValidRole_createsAndAssignsRole() {
        UserRequestDTO request = new UserRequestDTO("nuevo@test.com", "Nuevo", "Usuario", "password123", "MANAGER");
        given(keycloakAdminClient.createUser("nuevo@test.com", "nuevo@test.com", "Nuevo", "Usuario", "password123"))
                .willReturn("id-nuevo");

        UserResponseDTO result = userService.createUser(request);

        assertThat(result.id()).isEqualTo("id-nuevo");
        assertThat(result.username()).isEqualTo("nuevo@test.com");
        assertThat(result.role()).isEqualTo("MANAGER");
        assertThat(result.enabled()).isTrue();
        then(keycloakAdminClient).should().assignRealmRole("id-nuevo", "MANAGER");
    }

    @Test
    void createUser_withInvalidRole_throwsIllegalArgumentException_andNeverCallsKeycloak() {
        UserRequestDTO request = new UserRequestDTO("nuevo@test.com", "Nuevo", "Usuario", "password123", "SUPERADMIN");

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("role");

        then(keycloakAdminClient).should(never()).createUser(eq("nuevo@test.com"), eq("nuevo@test.com"), eq("Nuevo"), eq("Usuario"), eq("password123"));
    }

    @Test
    void setUserEnabled_delegatesToKeycloakAdminClient() {
        userService.setUserEnabled("id-1", false);

        then(keycloakAdminClient).should().setEnabled("id-1", false);
    }
}
