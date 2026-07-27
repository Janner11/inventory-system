package com.inventario.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.inventario.exception.KeycloakAdminException;
import com.inventario.exception.UserAlreadyExistsException;
import com.inventario.exception.UserNotFoundException;

/**
 * Cliente de bajo nivel contra la Keycloak Admin REST API (SEC-007), usado por
 * {@link com.inventario.service.UserService} para completar {@code user:manage} (sección 6
 * de CLAUDE.md: "scope existe en Keycloak; sin endpoint de gestión de usuarios en el
 * backend"). Se autentica como el propio service account de {@code inventario-backend}
 * (client_credentials, {@code serviceAccountsEnabled: true} desde SEC-001) con el rol de
 * cliente {@code manage-users} de {@code realm-management} otorgado en {@code realm.json}.
 * Sin dependencias nuevas: usa {@link RestClient} (incluido desde Spring Boot 3.2 vía
 * spring-boot-starter-web, ya presente en el proyecto).
 */
@Component
public class KeycloakAdminClient {

    private static final String CLIENT_ID = "inventario-backend";
    private static final Duration TOKEN_SAFETY_MARGIN = Duration.ofSeconds(15);

    private final RestClient restClient;
    private final String realm;
    private final String clientSecret;

    private volatile CachedToken cachedToken;

    public KeycloakAdminClient(
            @Value("${keycloak.admin.base-uri}") String baseUri,
            @Value("${keycloak.admin.realm}") String realm,
            @Value("${keycloak.admin.client-secret}") String clientSecret) {
        this.restClient = RestClient.create(baseUri);
        this.realm = realm;
        this.clientSecret = clientSecret;
    }

    public List<KeycloakUserRepresentation> listUsers() {
        return restClient.get()
                .uri("/admin/realms/{realm}/users?max=200", realm)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public KeycloakUserRepresentation getUser(String userId) {
        try {
            return restClient.get()
                    .uri("/admin/realms/{realm}/users/{userId}", realm, userId)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .retrieve()
                    .body(KeycloakUserRepresentation.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /**
     * Roles de realm asignados a un usuario. Deliberadamente por-usuario
     * ({@code .../users/{userId}/role-mappings/realm}) y no por-rol
     * ({@code .../roles/{roleName}/users}) — verificado contra el stack real que el
     * service account (con {@code view-users}/{@code manage-users}/{@code query-users}
     * de {@code realm-management}) recibe 403 ("unknown_error") en el segundo endpoint,
     * pese a que el primero funciona correctamente con los mismos permisos. Con el número
     * de usuarios de este proyecto (un puñado), N consultas por-usuario es más barato que
     * depurar por qué Keycloak deniega ese endpoint específico.
     */
    public List<KeycloakRoleRepresentation> getUserRealmRoles(String userId) {
        return restClient.get()
                .uri("/admin/realms/{realm}/users/{userId}/role-mappings/realm", realm, userId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    /** Crea el usuario en Keycloak y devuelve su id. Lanza {@link UserAlreadyExistsException} en 409. */
    public String createUser(String username, String email, String firstName, String lastName, String password) {
        CreateUserRequest body = new CreateUserRequest(
                username, email, firstName, lastName, true, true,
                List.of(new CredentialRepresentation("password", password, false)));
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/admin/realms/{realm}/users", realm)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            URI location = response.getHeaders().getLocation();
            if (location == null) {
                throw new KeycloakAdminException("Keycloak no devolvió la ubicación del usuario creado");
            }
            String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException.Conflict e) {
            throw new UserAlreadyExistsException(email);
        }
    }

    public void assignRealmRole(String userId, String roleName) {
        // GET /roles/{roleName} (obtener un rol por nombre) devuelve 403 "unknown_error"
        // para este service account pese a que sí tiene permiso para listar todos los
        // roles (mismo tipo de inconsistencia ya visto en getUserRealmRoles) — se busca
        // el rol dentro del listado completo en vez de pedirlo por nombre.
        KeycloakRoleRepresentation role = getRealmRoles().stream()
                .filter(r -> r.name().equals(roleName))
                .findFirst()
                .orElseThrow(() -> new KeycloakAdminException("Rol de realm no encontrado en Keycloak: " + roleName));

        restClient.post()
                .uri("/admin/realms/{realm}/users/{userId}/role-mappings/realm", realm, userId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    private List<KeycloakRoleRepresentation> getRealmRoles() {
        return restClient.get()
                .uri("/admin/realms/{realm}/roles", realm)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    /** Habilita/deshabilita el usuario (equivalente al soft delete de ADR-001, aplicado a cuentas). */
    public void setEnabled(String userId, boolean enabled) {
        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{userId}", realm, userId)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EnabledUpdateRequest(enabled))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new UserNotFoundException(userId);
        }
    }

    private String bearer() {
        return "Bearer " + getAccessToken();
    }

    synchronized String getAccessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.isValid()) {
            return current.accessToken();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", CLIENT_ID);
        form.add("client_secret", clientSecret);

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (HttpStatusCodeException e) {
            throw new KeycloakAdminException("No se pudo obtener un token de administración de Keycloak: " + e.getMessage());
        }

        if (response == null) {
            throw new KeycloakAdminException("Keycloak devolvió una respuesta vacía al solicitar el token de administración");
        }

        CachedToken fresh = new CachedToken(
                response.accessToken(),
                Instant.now().plusSeconds(response.expiresIn()).minus(TOKEN_SAFETY_MARGIN));
        cachedToken = fresh;
        return fresh.accessToken();
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {
    }

    private record CreateUserRequest(
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            boolean emailVerified,
            List<CredentialRepresentation> credentials) {
    }

    private record CredentialRepresentation(String type, String value, boolean temporary) {
    }

    private record EnabledUpdateRequest(boolean enabled) {
    }

    public record KeycloakUserRepresentation(
            String id,
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled) {
    }

    public record KeycloakRoleRepresentation(String id, String name) {
    }
}
