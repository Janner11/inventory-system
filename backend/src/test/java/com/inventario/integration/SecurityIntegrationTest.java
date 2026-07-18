package com.inventario.integration;

import com.inventario.config.JpaAuditingConfig;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * A diferencia de {@code ProductApiTest} (api/) y los *ControllerTest (unit/), que
 * mockean {@link org.springframework.security.oauth2.jwt.JwtDecoder} para aislar la
 * capa web, esta clase levanta un Keycloak real (Testcontainers) y ejercita el flujo
 * OAuth2 completo: password grant real, validacion de firma via JWKS real, y
 * extraccion de scopes de un JWT real (no construido a mano en el test) por
 * {@code JwtAuthConverter}. Es el unico punto del proyecto que prueba esta cadena
 * de punta a punta (TEST-002).
 */
// SEC-004: management.prometheus.metrics.export.enabled=true es necesario para que
// /actuator/prometheus responda en @SpringBootTest - sin esto, PrometheusMeterRegistry
// no se registra en el contexto de test (aunque si funciona normal en la app real) y el
// endpoint responde 404 en vez de aplicar la regla de seguridad. Mismo fix que
// AbstractApiTest (api/), encontrado al agregar el primer test que golpea este endpoint.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.prometheus.metrics.export.enabled=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class SecurityIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventario_test")
            .withUsername("test")
            .withPassword("test");

    // waitingFor(LOG_WAIT_STRATEGY) en vez del HttpWaitStrategy por defecto (que espera
    // /health/started vía red): en este entorno (Docker Desktop/Windows) el wait HTTP
    // falla de forma intermitente con "Unexpected end of file from server" pese a que
    // el contenedor arranca e importa el realm correctamente (confirmado en sus logs) -
    // un problema conocido de proxy de puertos de Docker Desktop, no del contenedor.
    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:24.0")
            .withRealmImportFile("keycloak/realm.json")
            .waitingFor(KeycloakContainer.LOG_WAIT_STRATEGY);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("keycloak.issuer-uri", () -> keycloak.getAuthServerUrl() + "/realms/inventario");
        registry.add("keycloak.jwk-set-uri",
                () -> keycloak.getAuthServerUrl() + "/realms/inventario/protocol/openid-connect/certs");
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        RestAssured.basePath = "/api";
    }

    private String realAccessToken(String username, String password) {
        return given()
                .baseUri(keycloak.getAuthServerUrl())
                .basePath("")
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "password")
                .formParam("client_id", "inventario-backend")
                .formParam("client_secret", "inventario-backend-secret")
                .formParam("username", username)
                .formParam("password", password)
                .when().post("/realms/inventario/protocol/openid-connect/token")
                .then().statusCode(200)
                .extract().path("access_token");
    }

    @Test
    void getProducts_sinToken_devuelve401() {
        given()
                .when().get("/products")
                .then().statusCode(401);
    }

    @Test
    void getProducts_conJwtMalformado_devuelve401() {
        given()
                .header("Authorization", "Bearer esto-no-es-un-jwt-valido")
                .when().get("/products")
                .then().statusCode(401);
    }

    @Test
    void getProducts_conTokenRealDeKeycloak_devuelve200() {
        String token = realAccessToken("admin@test.com", "admin123");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/products")
                .then().statusCode(200);
    }

    @Test
    void createProduct_conScopeInsuficiente_devuelve403() {
        String token = realAccessToken("viewer@test.com", "viewer123");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "Producto sin permiso", "sku", "SEC-INT-001", "category", "General",
                        "price", 10.00, "quantity", 1, "minStock", 1))
                .when().post("/products")
                .then().statusCode(403);
    }

    @Test
    void createProduct_conScopeCorrecto_devuelve201() {
        String token = realAccessToken("admin@test.com", "admin123");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "Producto con permiso", "sku", "SEC-INT-002", "category", "General",
                        "price", 10.00, "quantity", 1, "minStock", 1))
                .when().post("/products")
                .then().statusCode(201)
                .body("sku", org.hamcrest.Matchers.equalTo("SEC-INT-002"));
    }

    @Test
    void auditorSinScopeDeProducto_noPuedeListarProductos() {
        String token = realAccessToken("auditor@test.com", "auditor123");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/products")
                .then().statusCode(403);
    }

    @Test
    void ping_endpointPublico_noRequiereToken() {
        given()
                .when().get("/ping")
                .then().statusCode(200);
    }

    // SEC-004: /actuator/prometheus ya no es publico (exponia inventory_value/
    // products_critical/stock_movements sin autenticacion). El mecanismo real que usa
    // Prometheus en produccion es client_credentials, no un usuario humano - se prueba
    // exactamente ese flujo contra el Keycloak real, no un JWT construido a mano.
    private String serviceAccountToken() {
        return given()
                .baseUri(keycloak.getAuthServerUrl())
                .basePath("")
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", "inventario-backend")
                .formParam("client_secret", "inventario-backend-secret")
                .when().post("/realms/inventario/protocol/openid-connect/token")
                .then().statusCode(200)
                .extract().path("access_token");
    }

    @Test
    void actuatorPrometheus_sinToken_devuelve401() {
        given()
                .when().get("http://localhost:" + port + "/actuator/prometheus")
                .then().statusCode(401);
    }

    @Test
    void actuatorPrometheus_conTokenDeUsuarioSinScope_devuelve403() {
        String token = realAccessToken("viewer@test.com", "viewer123");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("http://localhost:" + port + "/actuator/prometheus")
                .then().statusCode(403);
    }

    @Test
    void actuatorPrometheus_conClientCredentialsReales_devuelve200() {
        String token = serviceAccountToken();

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("http://localhost:" + port + "/actuator/prometheus")
                .then().statusCode(200)
                .body(containsString("jvm_memory_used_bytes"));
    }

    @Test
    void actuatorHealth_siguePublico() {
        given()
                .when().get("http://localhost:" + port + "/actuator/health")
                .then().statusCode(200);
    }
}
