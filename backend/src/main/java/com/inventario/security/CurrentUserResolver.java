package com.inventario.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * Resuelve el usuario autenticado actual desde el {@code SecurityContext} (claim
 * {@code preferred_username} del JWT, o {@code sub} como fallback). Extraido de
 * {@link SecurityAuditorAware} para poder reutilizarse tambien desde
 * {@link com.inventario.audit.AuditRevisionListener}, que Hibernate instancia por
 * reflexion (no es un bean de Spring y no puede recibir esta logica via DI).
 */
public final class CurrentUserResolver {

    private static final String PREFERRED_USERNAME_CLAIM = "preferred_username";

    private CurrentUserResolver() {
    }

    public static Optional<String> resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            String preferredUsername = jwt.getClaimAsString(PREFERRED_USERNAME_CLAIM);
            return Optional.ofNullable(preferredUsername != null ? preferredUsername : jwt.getSubject());
        }

        return Optional.of(authentication.getName());
    }
}
