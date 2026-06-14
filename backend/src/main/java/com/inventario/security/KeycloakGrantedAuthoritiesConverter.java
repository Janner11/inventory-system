package com.inventario.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Extrae los client roles del claim {@code resource_access.<resourceId>.roles} del JWT
 * emitido por Keycloak y los convierte en {@link GrantedAuthority} con prefijo {@code SCOPE_}.
 */
public class KeycloakGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String AUTHORITY_PREFIX = "SCOPE_";

    private final String resourceId;

    public KeycloakGrantedAuthoritiesConverter(String resourceId) {
        this.resourceId = resourceId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS_CLAIM);
        if (resourceAccess == null) {
            return List.of();
        }

        Object resource = resourceAccess.get(resourceId);
        if (!(resource instanceof Map)) {
            return List.of();
        }

        Object roles = ((Map<String, Object>) resource).get(ROLES_CLAIM);
        if (!(roles instanceof Collection)) {
            return List.of();
        }

        return ((Collection<String>) roles).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(AUTHORITY_PREFIX + role))
                .toList();
    }
}
