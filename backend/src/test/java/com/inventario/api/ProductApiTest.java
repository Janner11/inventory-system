package com.inventario.api;

import com.inventario.config.JpaAuditingConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class ProductApiTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventario_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String VIEWER_TOKEN = "viewer-token";

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        RestAssured.basePath = "/api";

        when(jwtDecoder.decode(ADMIN_TOKEN))
                .thenReturn(buildJwt(ADMIN_TOKEN, List.of("product:view", "product:manage")));
        when(jwtDecoder.decode(VIEWER_TOKEN))
                .thenReturn(buildJwt(VIEWER_TOKEN, List.of("product:view")));
    }

    // ── Validación de permisos — 401 (sin token) ─────────────────────────────

    @Test
    void getAllProducts_sinToken_devuelve401() {
        given()
                .when().get("/products")
                .then().statusCode(401);
    }

    @Test
    void createProduct_sinToken_devuelve401() {
        given()
                .contentType(ContentType.JSON)
                .body(buildProductRequest("SKU-NO-AUTH-" + shortId()))
                .when().post("/products")
                .then().statusCode(401);
    }

    @Test
    void registerStockEntry_sinToken_devuelve401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("productId", UUID.randomUUID(), "quantity", 5, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then().statusCode(401);
    }

    // ── Validación de permisos — 403 (scope insuficiente) ────────────────────

    @Test
    void createProduct_conSoloViewScope_devuelve403() {
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .contentType(ContentType.JSON)
                .body(buildProductRequest("SKU-FORBIDDEN-" + shortId()))
                .when().post("/products")
                .then().statusCode(403);
    }

    @Test
    void updateProduct_conSoloViewScope_devuelve403() {
        String id = createProduct("SKU-UPD-PREP-" + shortId());
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .contentType(ContentType.JSON)
                .body(buildProductRequest("SKU-UPD-FORBIDDEN-" + shortId()))
                .when().put("/products/{id}", id)
                .then().statusCode(403);
    }

    @Test
    void registerStockEntry_conSoloViewScope_devuelve403() {
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", UUID.randomUUID(), "quantity", 5, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then().statusCode(403);
    }

    // ── Validación de errores — 400 / 404 ────────────────────────────────────

    @Test
    void createProduct_conDatosInvalidos_devuelve400() {
        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("sku", "SKU-BAD"))   // faltan name, category, price, etc.
                .when().post("/products")
                .then().statusCode(400);
    }

    @Test
    void getProductById_idInexistente_devuelve404() {
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/products/{id}", UUID.randomUUID())
                .then().statusCode(404);
    }

    // ── Rutas exitosas ────────────────────────────────────────────────────────

    @Test
    void getAllProducts_conViewScope_devuelve200() {
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/products")
                .then().statusCode(200);
    }

    @Test
    void createProduct_conDatosValidos_devuelve201YProductoCreado() {
        String sku = "SKU-NEW-" + shortId();
        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(ContentType.JSON)
                .body(buildProductRequest(sku))
                .when().post("/products")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("sku", equalTo(sku));
    }

    @Test
    void getProductById_idExistente_devuelve200YProducto() {
        String id = createProduct("SKU-GET-" + shortId());
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/products/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id));
    }

    @Test
    void updateProduct_conDatosValidos_devuelve200YProductoActualizado() {
        String id = createProduct("SKU-UPD-" + shortId());
        String nuevoSku = "SKU-UPD-V2-" + shortId();
        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(ContentType.JSON)
                .body(buildProductRequest(nuevoSku))
                .when().put("/products/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("sku", equalTo(nuevoSku));
    }

    @Test
    void deleteProduct_conManageScope_devuelve204() {
        String id = createProduct("SKU-DEL-" + shortId());
        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .when().delete("/products/{id}", id)
                .then().statusCode(204);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Jwt buildJwt(String tokenValue, List<String> roles) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject("test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("resource_access", Map.of("inventario-backend", Map.of("roles", roles)))
                .build();
    }

    private String createProduct(String sku) {
        return given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(ContentType.JSON)
                .body(buildProductRequest(sku))
                .when().post("/products")
                .then().statusCode(201)
                .extract().path("id");
    }

    private Map<String, Object> buildProductRequest(String sku) {
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

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
