package com.inventario.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Alimenta {@code @CreatedBy} ({@link com.inventario.entity.Product#createdBy}) con el
 * usuario del JWT autenticado (claim {@code preferred_username}, o el {@code sub} si ese
 * claim no esta presente). Unico bean {@link AuditorAware} del contexto, detectado
 * automaticamente por {@code @EnableJpaAuditing} (ver {@code JpaAuditingConfig}).
 */
@Component
public class SecurityAuditorAware implements AuditorAware<String> {

    private static final String PREFERRED_USERNAME_CLAIM = "preferred_username";

    @Override
    public Optional<String> getCurrentAuditor() {
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
