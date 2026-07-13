package com.inventario.unit;

import com.inventario.security.SecurityAuditorAware;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que {@link SecurityAuditorAware} (el {@code AuditorAware} que alimenta
 * {@code @CreatedBy} en {@link com.inventario.entity.Product}) resuelva el usuario
 * correcto segun el tipo de autenticacion presente en el {@code SecurityContext}.
 */
class SecurityAuditorAwareTest {

    private final SecurityAuditorAware auditorAware = new SecurityAuditorAware();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinAutenticacion_devuelveVacio() {
        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).isEmpty();
    }

    @Test
    void autenticacionAnonima_devuelveVacio() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void jwtConPreferredUsername_devuelveEseClaim() {
        SecurityContextHolder.getContext().setAuthentication(jwtAuthentication("admin@test.com"));

        assertThat(auditorAware.getCurrentAuditor()).contains("admin@test.com");
    }

    @Test
    void jwtSinPreferredUsername_usaElSubjectComoFallback() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThat(auditorAware.getCurrentAuditor()).contains("test-user");
    }

    @Test
    void autenticacionNoJwt_usaElNombreDeLaAutenticacion() {
        Authentication authentication = new TestingAuthenticationToken("service-account", null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(auditorAware.getCurrentAuditor()).contains("service-account");
    }

    private JwtAuthenticationToken jwtAuthentication(String preferredUsername) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("preferred_username", preferredUsername)
                .build();
        return new JwtAuthenticationToken(jwt, List.of());
    }
}
