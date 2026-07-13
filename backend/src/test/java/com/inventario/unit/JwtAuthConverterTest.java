package com.inventario.unit;

import com.inventario.security.JwtAuthConverter;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que {@link JwtAuthConverter} derive las authorities {@code SCOPE_*} unicamente
 * de los claims estandar de OAuth2 ({@code scope}/{@code scp}) y de los client roles de
 * Keycloak ({@code resource_access.inventario-backend.roles}) — nunca de roles de realm
 * (p. ej. ADMIN/MANAGER/VIEWER), que no deben otorgar acceso por si solos (sección 6 de
 * CLAUDE.md: "no validar autorización solo por nombre de rol").
 */
class JwtAuthConverterTest {

    private final JwtAuthConverter converter = new JwtAuthConverter();

    @Test
    void scopeClaimComoStringSeparadoPorEspacios_seConvierteEnAuthorities() {
        Jwt jwt = jwtBuilder().claim("scope", "product:view stock:view").build();

        Set<String> authorities = authoritiesOf(jwt);

        assertThat(authorities).containsExactlyInAnyOrder("SCOPE_product:view", "SCOPE_stock:view");
    }

    @Test
    void scpClaimComoLista_seConvierteEnAuthorities() {
        Jwt jwt = jwtBuilder().claim("scp", List.of("product:view", "product:manage")).build();

        Set<String> authorities = authoritiesOf(jwt);

        assertThat(authorities).containsExactlyInAnyOrder("SCOPE_product:view", "SCOPE_product:manage");
    }

    @Test
    void resourceAccessClientRoles_seConvierteEnAuthorities() {
        Jwt jwt = jwtBuilder()
                .claim("resource_access", Map.of("inventario-backend",
                        Map.of("roles", List.of("stock:manage", "audit:view"))))
                .build();

        Set<String> authorities = authoritiesOf(jwt);

        assertThat(authorities).containsExactlyInAnyOrder("SCOPE_stock:manage", "SCOPE_audit:view");
    }

    @Test
    void scopeYResourceAccess_seCombinanSinDuplicados() {
        Jwt jwt = jwtBuilder()
                .claim("scope", "product:view")
                .claim("resource_access", Map.of("inventario-backend",
                        Map.of("roles", List.of("product:view", "product:manage"))))
                .build();

        Set<String> authorities = authoritiesOf(jwt);

        assertThat(authorities).containsExactlyInAnyOrder("SCOPE_product:view", "SCOPE_product:manage");
    }

    @Test
    void resourceAccessDeOtroClient_noOtorgaAuthorities() {
        Jwt jwt = jwtBuilder()
                .claim("resource_access", Map.of("otro-client",
                        Map.of("roles", List.of("product:manage"))))
                .build();

        assertThat(authoritiesOf(jwt)).isEmpty();
    }

    @Test
    void claimsFaltantes_noRompenYNoOtorganAuthorities() {
        Jwt jwt = jwtBuilder().build();

        assertThat(authoritiesOf(jwt)).isEmpty();
    }

    @Test
    void rolesDeRealmComoAdminOManager_noOtorganAuthoritiesPorSiSolos() {
        // realm_access.roles es lo que expone Keycloak para roles de REALM (ADMIN, MANAGER,
        // WAREHOUSE, VIEWER, AUDITOR — sección 6 de CLAUDE.md). El converter no debe leer
        // este claim: la autorización es solo por scope individual, nunca por rol.
        Jwt jwt = jwtBuilder()
                .claim("realm_access", Map.of("roles", List.of("ADMIN", "MANAGER")))
                .build();

        assertThat(authoritiesOf(jwt)).isEmpty();
    }

    private Set<String> authoritiesOf(Jwt jwt) {
        AbstractAuthenticationToken token = converter.convert(jwt);
        return token.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
    }
}
