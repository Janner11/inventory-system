package com.inventario.config;

import com.inventario.security.JwtAuthConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configura el backend como OAuth2 Resource Server: valida el JWT emitido por Keycloak
 * (issuer-uri en application.yml) y extrae los scopes via {@link JwtAuthConverter}.
 * La autorización fina por endpoint se hace con {@code @PreAuthorize("hasAuthority('SCOPE_...')")}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Decodificador de JWT que separa el JWKS URI (red interna de Docker, usado para
     * obtener las llaves de firma) del issuer-uri publico (usado solo para validar el
     * claim {@code iss} del token, sin hacer discovery OIDC contra esa URL).
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${keycloak.jwk-set-uri}") String jwkSetUri,
            @Value("${keycloak.issuer-uri}") String issuerUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthConverter jwtAuthConverter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/**", "/api/ping").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)));

        return http.build();
    }
}
