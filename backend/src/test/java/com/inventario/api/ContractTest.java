package com.inventario.api;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.startsWith;

/**
 * Contract tests (TEST-003): valida que el documento OpenAPI expuesto en {@code /v3/api-docs}
 * sea coherente (paths principales presentes, codigos de respuesta documentados) y que las
 * respuestas reales de la API cumplan el JSON Schema de sus DTOs — "validacion manual" del
 * contrato (paso 8 del ticket), alternativa a una herramienta externa tipo schemathesis
 * (Python, fuera del stack Java/Gradle de este proyecto).
 */
class ContractTest extends AbstractApiTest {

    @Test
    void openApiSpec_esValidaYContieneLosPathsPrincipales() {
        given()
                .when().get("http://localhost:" + port + "/v3/api-docs")
                .then()
                .statusCode(200)
                .body("openapi", startsWith("3."))
                .body("paths", hasKey("/api/products"))
                .body("paths", hasKey("/api/products/{id}"))
                .body("paths", hasKey("/api/products/search"))
                .body("paths", hasKey("/api/products/critical"))
                .body("paths", hasKey("/api/products/stats"))
                .body("paths", hasKey("/api/stock/entry"))
                .body("paths", hasKey("/api/stock/exit"))
                .body("paths", hasKey("/api/stock/adjust"))
                .body("paths", hasKey("/api/stock/movements"))
                .body("paths", hasKey("/api/stock/movements/{productId}"))
                .body("paths", hasKey("/api/stock/alerts"))
                .body("paths", hasKey("/api/audit/products/{id}/revisions"));
    }

    @SuppressWarnings("unchecked") // navegacion de un documento OpenAPI generico (Map<String,Object> anidado)
    @Test
    void getProductById_documentaLosMismosCodigosDeRespuestaQueDevuelveEnLaPractica() {
        JsonPath spec = given()
                .when().get("http://localhost:" + port + "/v3/api-docs")
                .then().statusCode(200)
                .extract().jsonPath();

        Map<String, Object> paths = spec.getMap("paths");
        Map<String, Object> productByIdPath = (Map<String, Object>) paths.get("/api/products/{id}");
        Map<String, Object> getOperation = (Map<String, Object>) productByIdPath.get("get");
        Map<String, Object> documentedResponses = (Map<String, Object>) getOperation.get("responses");

        assertThat(documentedResponses).containsKeys("200", "401", "403", "404");

        // El comportamiento real coincide con lo documentado.
        String id = createProduct("SKU-CONTRACT-" + shortId());
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/products/{id}", id)
                .then().statusCode(200);
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/products/{id}", UUID.randomUUID())
                .then().statusCode(404);
        given()
                .when().get("/products/{id}", id)
                .then().statusCode(401);
    }

    @Test
    void createProduct_response_cumpleElJsonSchemaDeProductResponseDTO() {
        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(ContentType.JSON)
                .body(buildProductRequest("SKU-SCHEMA-" + shortId()))
                .when().post("/products")
                .then()
                .statusCode(201)
                .body(matchesJsonSchemaInClasspath("schemas/product-response-schema.json"));
    }

    @Test
    void getAllProducts_response_cumpleElJsonSchemaDePagina() {
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/products")
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/product-page-schema.json"));
    }

    @Test
    void registerStockEntry_response_cumpleElJsonSchemaDeStockMovementResponseDTO() {
        String productId = createProduct("SKU-SCHEMA-STOCK-" + shortId());

        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", productId, "quantity", 3, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then()
                .statusCode(201)
                .body(matchesJsonSchemaInClasspath("schemas/stock-movement-response-schema.json"));
    }
}
