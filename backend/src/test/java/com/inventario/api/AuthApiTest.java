package com.inventario.api;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.Mockito.when;

/**
 * Tests de API (RestAssured) genericos de autenticacion/autorizacion (TEST-003), usando
 * {@code /api/products} como endpoint representativo protegido: sin token, token
 * malformado, token expirado, scope insuficiente, y verificacion de headers de
 * seguridad/rutas publicas. Los tests de autorizacion especificos de cada recurso
 * (scope correcto vs incorrecto por endpoint) viven en {@link ProductApiTest}/
 * {@link StockApiTest}.
 *
 * <p>Para el flujo OAuth2 real de punta a punta (Keycloak real, sin mockear
 * {@code JwtDecoder}) ver {@code SecurityIntegrationTest} (integration/, TEST-002).
 */
class AuthApiTest extends AbstractApiTest {

    private static final String MALFORMED_TOKEN = "esto-no-es-un-jwt-valido";
    private static final String EXPIRED_TOKEN = "expired-token";

    @Test
    void sinToken_devuelve401() {
        given()
                .when().get("/products")
                .then().statusCode(401);
    }

    @Test
    void conTokenMalformado_devuelve401() {
        when(jwtDecoder.decode(MALFORMED_TOKEN))
                .thenThrow(new BadJwtException("Malformed JWT"));

        given()
                .header("Authorization", "Bearer " + MALFORMED_TOKEN)
                .when().get("/products")
                .then().statusCode(401);
    }

    @Test
    void conTokenExpirado_devuelve401() {
        when(jwtDecoder.decode(EXPIRED_TOKEN))
                .thenThrow(new BadJwtException("Jwt expired at 2020-01-01T00:00:00Z"));

        given()
                .header("Authorization", "Bearer " + EXPIRED_TOKEN)
                .when().get("/products")
                .then().statusCode(401);
    }

    @Test
    void conScopeIncorrecto_devuelve403() {
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .contentType(ContentType.JSON)
                .body(buildProductRequest("SKU-AUTH-" + shortId()))
                .when().post("/products")
                .then().statusCode(403);
    }

    @Test
    void conScopeCorrecto_devuelve200() {
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/products")
                .then().statusCode(200);
    }

    @Test
    void headersDeSeguridad_estanPresentesEnLaRespuesta() {
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/products")
                .then()
                .statusCode(200)
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void preflightOptions_sinToken_noRequiereAutenticacion() {
        // SecurityConfig permite explicitamente OPTIONS en cualquier ruta (preflight CORS).
        given()
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .when().options("/products")
                .then().statusCode(org.hamcrest.Matchers.not(401));
    }

    @Test
    void actuatorHealth_esPublico() {
        given()
                .when().get("http://localhost:" + port + "/actuator/health")
                .then().statusCode(200);
    }

    // SEC-004: /actuator/prometheus expone metricas de negocio (inventory_value,
    // products_critical, stock_movements) y ya no debe ser publico como /actuator/health.
    @Test
    void actuatorPrometheus_sinToken_devuelve401() {
        given()
                .when().get("http://localhost:" + port + "/actuator/prometheus")
                .then().statusCode(401);
    }

    @Test
    void actuatorPrometheus_conScopeInsuficiente_devuelve403() {
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("http://localhost:" + port + "/actuator/prometheus")
                .then().statusCode(403);
    }

    @Test
    void actuatorPrometheus_conScopeCorrecto_devuelve200() {
        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .when().get("http://localhost:" + port + "/actuator/prometheus")
                .then().statusCode(200);
    }

    @Test
    void swaggerUi_esPublico() {
        given()
                .when().get("http://localhost:" + port + "/v3/api-docs")
                .then().statusCode(200);
    }

    @Test
    void ping_esPublico() {
        given()
                .when().get("/ping")
                .then().statusCode(200);
    }

    @Test
    void registerStockEntry_sinToken_devuelve401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("productId", UUID.randomUUID(), "quantity", 5, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then().statusCode(401);
    }
}
