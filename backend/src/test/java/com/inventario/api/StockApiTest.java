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
 * Tests de API (RestAssured) para {@code /api/stock/**} (TEST-003): entradas, salidas,
 * ajustes, historial de movimientos y alertas de stock bajo.
 */
class StockApiTest extends AbstractApiTest {

    // ── Validación de permisos — 401 / 403 ───────────────────────────────────

    @Test
    void registerEntry_sinToken_devuelve401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("productId", UUID.randomUUID(), "quantity", 5, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then().statusCode(401);
    }

    @Test
    void registerEntry_conSoloViewScope_devuelve403() {
        given()
                .header("Authorization", "Bearer " + VIEWER_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", UUID.randomUUID(), "quantity", 5, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then().statusCode(403);
    }

    @Test
    void registerEntry_conSoloProductManageScope_devuelve403() {
        // product:manage ya no basta para /stock/entry: el scope dedicado es stock:manage.
        String productId = createProduct("SKU-STOCK-PROD-" + shortId());
        given()
                .header("Authorization", "Bearer " + PRODUCT_MANAGER_ONLY_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", productId, "quantity", 5, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then().statusCode(403);
    }

    @Test
    void getRecentMovements_sinToken_devuelve401() {
        given()
                .when().get("/stock/movements")
                .then().statusCode(401);
    }

    @Test
    void getRecentMovements_conSoloManageScope_devuelve403() {
        // GET /stock/movements exige stock:view, no stock:manage.
        given()
                .header("Authorization", "Bearer " + PRODUCT_MANAGER_ONLY_TOKEN)
                .when().get("/stock/movements")
                .then().statusCode(403);
    }

    // ── Validación de errores — 400 / 404 / 422 ──────────────────────────────

    @Test
    void registerEntry_conDatosInvalidos_devuelve400() {
        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", UUID.randomUUID(), "quantity", 0, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then().statusCode(400);
    }

    // BACK-009: antes de @Size en StockMovementRequestDTO.performedBy, este mismo request
    // llegaba hasta la base de datos (VARCHAR(255), ver V4__create_stock_movements_table.sql)
    // y fallaba con un DataIntegrityViolationException reportado como "recurso ya existe o
    // esta en uso" - el mismo mensaje que un SKU duplicado, pese a no tener nada que ver.
    // Solo alcanzable llamando la API directamente (no es un campo editable en la UI).
    @Test
    void registerEntry_conPerformedByExcedeLongitud_devuelve400ConMensajeQueMencionaElCampo() {
        String productId = createProduct("SKU-STOCK-PERFBY-" + shortId());
        String longPerformedBy = "x".repeat(256);

        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", productId, "quantity", 5, "performedBy", longPerformedBy))
                .when().post("/stock/entry")
                .then()
                .statusCode(400)
                .body("message", org.hamcrest.Matchers.containsString("performedBy"))
                .body("message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ya existe")));
    }

    @Test
    void registerEntry_conProductoInexistente_devuelve404() {
        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", UUID.randomUUID(), "quantity", 5, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then().statusCode(404);
    }

    @Test
    void registerExit_conStockInsuficiente_devuelve422() {
        String productId = createProduct("SKU-STOCK-INSF-" + shortId());
        // El producto se crea con quantity=10 (ver AbstractApiTest.buildProductRequest); pedir 100 de salida excede el disponible.
        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", productId, "quantity", 100, "performedBy", "tester"))
                .when().post("/stock/exit")
                .then().statusCode(422);
    }

    @Test
    void getMovementsByProduct_productoInexistente_devuelve404() {
        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .when().get("/stock/movements/{productId}", UUID.randomUUID())
                .then().statusCode(404);
    }

    @Test
    void adjustStock_sinCambioReal_devuelve400() {
        String productId = createProduct("SKU-STOCK-NOOP-" + shortId());
        // El producto se crea con quantity=10; ajustar a la misma cantidad no genera cambio real.
        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", productId, "newQuantity", 10, "performedBy", "tester"))
                .when().post("/stock/adjust")
                .then().statusCode(400);
    }

    // ── Rutas exitosas ────────────────────────────────────────────────────────

    @Test
    void registerEntry_conStockManageScope_devuelve201() {
        String productId = createProduct("SKU-STOCK-ENTRY-" + shortId());
        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", productId, "quantity", 5, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("type", equalTo("ENTRY"))
                .body("newQuantity", equalTo(15));
    }

    @Test
    void registerExit_conStockManageScope_devuelve201() {
        String productId = createProduct("SKU-STOCK-EXIT-" + shortId());
        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", productId, "quantity", 4, "performedBy", "tester"))
                .when().post("/stock/exit")
                .then()
                .statusCode(201)
                .body("type", equalTo("EXIT"))
                .body("newQuantity", equalTo(6));
    }

    @Test
    void adjustStock_conStockManageScope_devuelve201() {
        String productId = createProduct("SKU-STOCK-ADJ-" + shortId());
        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", productId, "newQuantity", 7, "reason", "Conteo fisico", "performedBy", "tester"))
                .when().post("/stock/adjust")
                .then()
                .statusCode(201)
                .body("type", equalTo("ADJUSTMENT"))
                .body("newQuantity", equalTo(7));
    }

    @Test
    void getRecentMovements_conStockViewScope_devuelve200Paginado() {
        String productId = createProduct("SKU-STOCK-HIST-" + shortId());
        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", productId, "quantity", 1, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .queryParam("size", 5)
                .when().get("/stock/movements")
                .then()
                .statusCode(200)
                .body("content.size()", greaterThanOrEqualTo(1))
                .body("totalElements", greaterThanOrEqualTo(1));
    }

    @Test
    void getMovementsByProduct_conStockViewScope_devuelve200ConSoloEseProducto() {
        String productId = createProduct("SKU-STOCK-BYPROD-" + shortId());
        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of("productId", productId, "quantity", 2, "performedBy", "tester"))
                .when().post("/stock/entry")
                .then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .when().get("/stock/movements/{productId}", productId)
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].productId", equalTo(productId));
    }

    @Test
    void getLowStockAlerts_conStockViewScope_devuelve200() {
        String sku = "SKU-STOCK-ALERT-" + shortId();
        given()
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "Producto en alerta", "sku", sku, "category", "Test",
                        "price", 5.00, "quantity", 1, "minStock", 10))
                .when().post("/products")
                .then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + WAREHOUSE_TOKEN)
                .when().get("/stock/alerts")
                .then()
                .statusCode(200)
                .body("sku", org.hamcrest.Matchers.hasItem(sku));
    }
}
