package com.inventario.exception;

/** Fallo inesperado de comunicación con la Keycloak Admin REST API (SEC-007). */
public class KeycloakAdminException extends RuntimeException {

    public KeycloakAdminException(String message) {
        super(message);
    }
}
