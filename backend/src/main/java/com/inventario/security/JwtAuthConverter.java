package com.inventario.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Convierte un JWT de Keycloak en un {@link JwtAuthenticationToken} cuyas authorities combinan
 * los scopes estandar del token (claim {@code scope}) con los client roles de
 * {@code inventario-backend} (ver {@link KeycloakGrantedAuthoritiesConverter}).
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String RESOURCE_ID = "inventario-backend";

    private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
    private final KeycloakGrantedAuthoritiesConverter keycloakConverter =
            new KeycloakGrantedAuthoritiesConverter(RESOURCE_ID);

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Set<GrantedAuthority> authorities = Stream.concat(
                        defaultConverter.convert(jwt).stream(),
                        keycloakConverter.convert(jwt).stream())
                .collect(Collectors.toCollection(HashSet::new));

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
