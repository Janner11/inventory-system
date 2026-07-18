package com.inventario.integration;

import com.inventario.config.JpaAuditingConfig;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

    // SEC-006: revokeRefreshToken en realm.json (Keycloak revoca un refresh token en
    // cuanto se usa para pedir un par nuevo, no solo cuando expira). Se usan
    // "warehouse@test.com"/"manager@test.com" (no usados en ningun otro test de esta
    // clase) para no interferir con los demas tests, y para que la simulacion de
    // reuso (que termina revocando toda la cadena de refresh de esa sesion, ver el
    // primer test) no contamine la verificacion del flujo normal (segundo test).
    private String initialRefreshToken(String username, String password) {
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
                .extract().path("refresh_token");
    }

    private Response refreshGrant(String refreshToken) {
        return given()
                .baseUri(keycloak.getAuthServerUrl())
                .basePath("")
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "refresh_token")
                .formParam("client_id", "inventario-backend")
                .formParam("client_secret", "inventario-backend-secret")
                .formParam("refresh_token", refreshToken)
                .when().post("/realms/inventario/protocol/openid-connect/token");
    }

    @Test
    void refreshToken_yaRotado_esRechazadoAlReusarse() {
        String refreshTokenA = initialRefreshToken("warehouse@test.com", "warehouse123");

        // Uso legitimo: la app pide un par nuevo con el refresh token A (rotacion).
        Response rotated = refreshGrant(refreshTokenA);
        rotated.then().statusCode(200);
        String refreshTokenB = rotated.jsonPath().getString("refresh_token");
        assertNotEquals(refreshTokenA, refreshTokenB);

        // Reusar el refresh token A, ya rotado: debe ser rechazado (revokeRefreshToken).
        // No se asume un codigo de estado especifico (SEC-005 confirmo que Keycloak no
        // siempre usa 400 para invalid_grant) - se verifica que la respuesta no otorga
        // un access_token nuevo, que es la garantia real que pide el ticket.
        refreshGrant(refreshTokenA)
                .then()
                .statusCode(not(200))
                .body("access_token", nullValue());

        // Hallazgo real (no asumido de antemano): al detectar el reuso de un token ya
        // rotado, Keycloak no se limita a rechazar ESE token - revoca toda la cadena de
        // refresh de la sesion (deteccion de robo/replay). El propio token B, legitimo
        // y nunca antes usado, tambien queda invalido a partir de aqui. Es un
        // comportamiento de seguridad correcto (y mas estricto de lo que asumia la
        // primera version de este test) - se verifica explicitamente en vez de
        // ignorarlo, para no dejar pasar una regresion futura si Keycloak deja de
        // revocar la sesion completa.
        refreshGrant(refreshTokenB)
                .then()
                .statusCode(not(200))
                .body("access_token", nullValue());
    }

    @Test
    void refreshToken_flujoNormalDeRotacion_siguFuncionando() {
        // Sesion propia (usuario distinto), sin ningun reuso de por medio - reproduce
        // exactamente lo que hace AuthContext.jsx con keycloak.updateToken(30): pedir un
        // par nuevo con el refresh token vigente, una sola vez.
        String refreshToken = initialRefreshToken("manager@test.com", "manager123");

        refreshGrant(refreshToken)
                .then()
                .statusCode(200)
                .body("access_token", org.hamcrest.Matchers.notNullValue());
    }
}
