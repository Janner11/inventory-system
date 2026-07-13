package com.inventario.api;

import com.inventario.config.JpaAuditingConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.Mockito.when;

/**
 * Base compartida para los tests de API con RestAssured (TEST-003):
 * {@code ProductApiTest}, {@code StockApiTest}, {@code AuthApiTest} y {@code ContractTest}
 * extienden esta clase y comparten un unico Postgres (patron "singleton container" de
 * Testcontainers: se arranca una sola vez en un bloque estatico, SIN {@code @Testcontainers}/
 * {@code @Container} — esas anotaciones detienen el contenedor en el {@code afterAll} de
 * CADA clase, lo que rompe el contrato de "compartido" cuando el campo esta heredado desde
 * una clase base: la 2a subclase en correr encuentra el contenedor detenido y, aunque Spring
 * reutilice el contexto cacheado, el DataSource queda apuntando a un puerto que ya no existe.
 * Con el bloque estatico, el contenedor vive hasta que Ryuk lo cierra al terminar la JVM de
 * test — ver la advertencia oficial de Testcontainers sobre "Singleton containers").
 * Tambien comparten un {@link JwtDecoder} mockeado para construir JWTs de prueba con scopes
 * arbitrarios sin depender de un realm real.
 *
 * <p>A diferencia de {@code SecurityIntegrationTest} (integration/, TEST-002), que levanta
 * un Keycloak real (Testcontainers) para probar el flujo OAuth2 de punta a punta, esta
 * familia de tests mockea {@link JwtDecoder} para aislar la capa de API/contrato del
 * proveedor de identidad — mas rapido y determinista, a costa de no probar la validacion
 * de firma/issuer real (eso ya lo cubre SecurityIntegrationTest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
abstract class AbstractApiTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventario_test")
            .withUsername("test")
            .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    protected int port;

    @MockBean
    protected JwtDecoder jwtDecoder;

    protected static final String ADMIN_TOKEN = "admin-token";
    protected static final String VIEWER_TOKEN = "viewer-token";
    protected static final String WAREHOUSE_TOKEN = "warehouse-token";
    protected static final String AUDITOR_TOKEN = "auditor-token";
    protected static final String PRODUCT_MANAGER_ONLY_TOKEN = "product-manager-only-token";

    @BeforeEach
    void baseSetUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        RestAssured.basePath = "/api";

        when(jwtDecoder.decode(ADMIN_TOKEN))
                .thenReturn(buildJwt(ADMIN_TOKEN, List.of(
                        "product:view", "product:manage", "stock:view", "stock:manage",
                        "report:view", "user:manage", "audit:view")));
        when(jwtDecoder.decode(VIEWER_TOKEN))
                .thenReturn(buildJwt(VIEWER_TOKEN, List.of("product:view", "stock:view")));
        when(jwtDecoder.decode(WAREHOUSE_TOKEN))
                .thenReturn(buildJwt(WAREHOUSE_TOKEN, List.of("product:view", "stock:view", "stock:manage")));
        when(jwtDecoder.decode(AUDITOR_TOKEN))
                .thenReturn(buildJwt(AUDITOR_TOKEN, List.of("audit:view", "report:view")));
        when(jwtDecoder.decode(PRODUCT_MANAGER_ONLY_TOKEN))
                .thenReturn(buildJwt(PRODUCT_MANAGER_ONLY_TOKEN, List.of("product:view", "product:manage")));
    }

    protected Jwt buildJwt(String tokenValue, List<String> roles) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject("test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("resource_access", Map.of("inventario-backend", Map.of("roles", roles)))
                .build();
    }

    /** Crea un producto con el token de administrador y devuelve su id (String, formato UUID). */
    protected String createProduct(String sku) {
        return given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(ContentType.JSON)
                .body(buildProductRequest(sku))
                .when().post("/products")
                .then().statusCode(201)
                .extract().path("id");
    }

    protected Map<String, Object> buildProductRequest(String sku) {
        return Map.of(
                "name", "Producto de prueba",
                "sku", sku,
                "description", "Descripcion de prueba",
                "category", "Test",
                "price", 9.99,
                "quantity", 10,
                "minStock", 2
        );
    }

    protected String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
