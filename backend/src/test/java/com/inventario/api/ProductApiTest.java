package com.inventario.api;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests de API (RestAssured) para {@code /api/products} y, dado que su historial de
 * revisiones esta directamente ligado a Product, para {@code /api/audit/products/{id}/revisions}
 * (TEST-003). Los tests de {@code /api/stock/**} viven en {@link StockApiTest}; los tests
 * genericos de autenticacion/autorizacion (401/403/token expirado/headers) en {@link AuthApiTest}.
 */
class ProductApiTest extends AbstractApiTest {

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
    void getProductStats_conSoloProductViewScope_devuelve403() {
        // /products/stats exige report:view, no product:view.
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/products/stats")
                .then().statusCode(403);
    }

    // ── Validación de errores — 400 / 404 / 409 ──────────────────────────────

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
    void createProduct_conSkuDuplicado_devuelve409() {
        String sku = "SKU-DUP-" + shortId();
        createProduct(sku);

        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(ContentType.JSON)
                .body(buildProductRequest(sku))
                .when().post("/products")
                .then().statusCode(409);
    }

    @Test
    void getProductById_idInexistente_devuelve404() {
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/products/{id}", UUID.randomUUID())
                .then().statusCode(404);
    }

    @Test
    void updateProduct_idInexistente_devuelve404() {
        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(ContentType.JSON)
                .body(buildProductRequest("SKU-UPD-404-" + shortId()))
                .when().put("/products/{id}", UUID.randomUUID())
                .then().statusCode(404);
    }

    @Test
    void deleteProduct_idInexistente_devuelve404() {
        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .when().delete("/products/{id}", UUID.randomUUID())
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
    void getAllProducts_conPaginacion_devuelvePaginaConMetadatosCorrectos() {
        String category = "PAG-" + shortId();
        for (int i = 0; i < 3; i++) {
            given()
                    .header("Authorization", "Bearer " + ADMIN_TOKEN)
                    .contentType(ContentType.JSON)
                    .body(Map.of(
                            "name", "Producto paginado " + i, "sku", "SKU-PAG-" + shortId(),
                            "category", category, "price", 5.00, "quantity", 1, "minStock", 1))
                    .when().post("/products")
                    .then().statusCode(201);
        }

        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .queryParam("category", category)
                .queryParam("page", 0)
                .queryParam("size", 2)
                .when().get("/products")
                .then()
                .statusCode(200)
                .body("content.size()", equalTo(2))
                .body("totalElements", equalTo(3))
                .body("totalPages", equalTo(2))
                .body("number", equalTo(0))
                .body("size", equalTo(2));
    }

    @Test
    void searchProducts_conQueryCoincidente_devuelveElProducto() {
        String sku = "SKU-SEARCH-" + shortId();
        createProduct(sku);

        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .queryParam("q", sku)
                .when().get("/products/search")
                .then()
                .statusCode(200)
                .body("totalElements", greaterThanOrEqualTo(1))
                .body("content[0].sku", equalTo(sku));
    }

    @Test
    void getCriticalProducts_devuelveProductosBajoStockMinimo() {
        String sku = "SKU-CRIT-" + shortId();
        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "Producto critico", "sku", sku, "category", "Test",
                        "price", 5.00, "quantity", 1, "minStock", 10))
                .when().post("/products")
                .then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/products/critical")
                .then()
                .statusCode(200)
                .body("sku", org.hamcrest.Matchers.hasItem(sku));
    }

    @Test
    void getProductStats_conReportViewScope_devuelve200() {
        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .when().get("/products/stats")
                .then()
                .statusCode(200)
                .body("totalProducts", greaterThanOrEqualTo(0));
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

    // ── Auditoria — scope dedicado audit:view (SEC-002) ──────────────────────

    @Test
    void auditRevisions_sinToken_devuelve401() {
        given()
                .when().get("/audit/products/{id}/revisions", UUID.randomUUID())
                .then().statusCode(401);
    }

    @Test
    void auditRevisions_conProductViewScope_devuelve403() {
        // product:view ya no basta para /audit/**: el scope dedicado es audit:view.
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .when().get("/audit/products/{id}/revisions", UUID.randomUUID())
                .then().statusCode(403);
    }

    @Test
    void auditRevisions_conAuditViewScope_devuelve200ConHistorial() {
        String id = createProduct("SKU-AUDIT-" + shortId());
        given()
                .header("Authorization", "Bearer " + AUDITOR_TOKEN)
                .when().get("/audit/products/{id}/revisions", id)
                .then()
                .statusCode(200)
                .body("[0].revisionType", equalTo("ADD"));
    }
}
