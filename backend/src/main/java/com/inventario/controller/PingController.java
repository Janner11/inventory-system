package com.inventario.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "service", "inventario-backend",
                "timestamp", Instant.now().toString()
        );
    }

    /**
     * Endpoint de prueba para validar el flujo OAuth2/JWT de Keycloak (SEC-001):
     * requiere un token con el scope {@code product:view}.
     */
    @GetMapping("/api/ping/secure")
    @PreAuthorize("hasAuthority('SCOPE_product:view')")
    public Map<String, Object> securePing(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "status", "ok",
                "service", "inventario-backend",
                "user", jwt.getClaimAsString("preferred_username"),
                "timestamp", Instant.now().toString()
        );
    }
}
