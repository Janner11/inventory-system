# Casos de Prueba Manuales

Casos de prueba ejecutables manualmente contra el stack real
(`docker compose -f docker-compose.dev.yml up -d` + frontend en
`npm run dev`), organizados por módulo y trazables a los requisitos de
[`docs/requirements.md`](../requirements.md). Estos casos complementan —no
reemplazan— la automatización real del proyecto (248 tests de backend, 62 de
frontend, 54 E2E — ver [`testing-strategy.md`](testing-strategy.md)); están
pensados para ejecución manual exploratoria/de regresión rápida, por
ejemplo antes de un release.

**Convención de estado:** cada caso indica `✅ Ejecutado` si se corrió
manualmente contra el stack real durante la redacción de este documento
(con su resultado), o `⬜ Por ejecutar` si describe un flujo ya cubierto por
la automatización (unit/API/E2E, referenciada en cada caso) pero no
re-ejecutado a mano en esta sesión.

## Usuarios de prueba usados en estos casos

| Usuario | Password | Rol | Uso típico en los casos |
|---|---|---|---|
| `admin@test.com` | `admin123` | ADMIN | Casos de gestión completa |
| `manager@test.com` | `manager123` | MANAGER | Casos de gestión sin auditoría |
| `warehouse@test.com` | `warehouse123` | WAREHOUSE | Casos de stock sin gestión de productos |
| `viewer@test.com` | `viewer123` | VIEWER | Casos de solo lectura / restricción de permisos |
| `auditor@test.com` | `auditor123` | AUDITOR | Casos de auditoría/reportes |

## Módulo: Autenticación (RF-01 a RF-06)

### TC-AUTH-01 — Login exitoso

| Campo | Detalle |
|---|---|
| Precondición | Stack levantado, usuario en `/` sin sesión |
| Pasos | 1. Click en "Iniciar sesión". 2. Ingresar `admin@test.com`/`admin123` en el formulario de Keycloak. 3. Enviar. |
| Resultado esperado | Redirige a `/dashboard`; el navbar muestra `admin@test.com` |
| Estado | ✅ Ejecutado — ver evidencia en [`user-manual.md`](../user-manual.md#iniciar-sesión) |

### TC-AUTH-02 — Ruta protegida sin sesión

| Campo | Detalle |
|---|---|
| Precondición | Sin sesión activa |
| Pasos | 1. Navegar directamente a `http://localhost:5173/dashboard`. |
| Resultado esperado | Redirige a `/` (pantalla de login), no muestra contenido protegido |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `auth.spec.js` ("ruta protegida sin sesión regresa a la pantalla de inicio") |

### TC-AUTH-03 — Token inválido rechazado por la API

| Campo | Detalle |
|---|---|
| Precondición | — |
| Pasos | `curl -i -H "Authorization: Bearer token-invalido" http://localhost:8081/api/products` |
| Resultado esperado | `401 Unauthorized` |
| Estado | ✅ Ejecutado — ver [Ejecución real](#ejecución-real-de-un-caso-durante-esta-sesión) |

### TC-AUTH-04 — Logout

| Campo | Detalle |
|---|---|
| Precondición | Sesión activa |
| Pasos | 1. Click en "Cerrar sesión" (navbar). |
| Resultado esperado | Redirige a `/`; una nueva petición a un endpoint protegido sin volver a loguearse devuelve 401 |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `auth.spec.js` |

### TC-AUTH-05 — Matriz de permisos por rol

| Campo | Detalle |
|---|---|
| Precondición | Los 5 usuarios de prueba |
| Pasos | Para cada usuario, obtener un JWT (`docs/security/keycloak.md#operación--comandos-útiles`) y llamar `GET /api/products`, `POST /api/stock/entry`, `GET /api/audit/products/{id}/revisions` |
| Resultado esperado | Los scopes resueltos coinciden exactamente con la tabla de `docs/security/keycloak.md#roles-de-realm-composite` |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `SecurityIntegrationTest`/`AuthApiTest` |

### TC-AUTH-06 — La UI oculta controles de gestión a `viewer`

| Campo | Detalle |
|---|---|
| Precondición | Login como `viewer@test.com` |
| Pasos | 1. Ir a `/products`. |
| Resultado esperado | No aparece el botón "Nuevo producto" ni "Editar"/"Eliminar" en la tabla; sí aparece "Ver" |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `permissions.spec.js` |

## Módulo: Productos (RF-10 a RF-19)

### TC-PROD-01 — Listado paginado

| Campo | Detalle |
|---|---|
| Precondición | Login como `admin@test.com`, más de 5 productos activos |
| Pasos | 1. Ir a `/products`. |
| Resultado esperado | Se muestran 5 productos y el control "Página X de Y"; "Siguiente" avanza de página |
| Estado | ✅ Ejecutado — 93 productos activos en la base de datos local, paginación confirmada |

### TC-PROD-02 — Búsqueda por nombre/SKU

| Campo | Detalle |
|---|---|
| Pasos | 1. Escribir un SKU parcial en el buscador. |
| Resultado esperado | La tabla se filtra a los productos coincidentes (server-side) |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `ProductsPage.test.jsx`/`ProductApiTest` |

### TC-PROD-03 — Filtro por categoría

| Campo | Detalle |
|---|---|
| Pasos | 1. Seleccionar una categoría en el `<select>`. |
| Resultado esperado | Solo se muestran productos de esa categoría |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `products.spec.js` |

### TC-PROD-04 — Filtro "Solo stock bajo"

| Campo | Detalle |
|---|---|
| Pasos | 1. Activar el checkbox "Solo stock bajo". |
| Resultado esperado | Solo se muestran productos con `quantity < minStock`, con badge "Stock bajo" |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `ProductsPage.test.jsx` |

### TC-PROD-05 — Crear producto con datos válidos

| Campo | Detalle |
|---|---|
| Precondición | Login como `admin@test.com` |
| Pasos | 1. "Nuevo producto". 2. Completar nombre, SKU (minúsculas), categoría, precio, cantidad, stock mínimo. 3. Guardar. |
| Resultado esperado | `201 Created`; redirige a `/products`; el producto aparece con el SKU en **mayúsculas** |
| Estado | ✅ Ejecutado — ver evidencia en [`user-manual.md`](../user-manual.md#crear-un-producto) |

### TC-PROD-06 — Crear producto con SKU duplicado

| Campo | Detalle |
|---|---|
| Pasos | 1. Repetir el SKU de un producto existente y guardar. |
| Resultado esperado | `409 Conflict`; mensaje "Ya existe un producto con el SKU: ..." visible en el formulario |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `ProductForm.test.jsx`/`ProductApiTest` |

### TC-PROD-07 — Editar producto

| Campo | Detalle |
|---|---|
| Pasos | 1. "Editar" sobre un producto existente. 2. Cambiar cantidad. 3. Guardar. |
| Resultado esperado | El formulario precarga los datos reales; `200 OK`; el cambio se refleja en la tabla |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `products.spec.js` |

### TC-PROD-08 — Eliminar producto (soft delete)

| Campo | Detalle |
|---|---|
| Pasos | 1. "Eliminar" sobre un producto. 2. Confirmar el diálogo. |
| Resultado esperado | El producto desaparece de la lista (status `ACTIVE`); sigue existiendo en la base de datos como `INACTIVE` |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `products.spec.js` |

### TC-PROD-09 — Validación de formulario vacío

| Campo | Detalle |
|---|---|
| Pasos | 1. "Nuevo producto". 2. Click "Guardar" sin llenar nada. |
| Resultado esperado | Errores bajo cada campo obligatorio; **sin** llamada a la API |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `ProductForm.test.jsx` |

### TC-PROD-10 — Estadísticas del catálogo

| Campo | Detalle |
|---|---|
| Pasos | `curl -H "Authorization: Bearer <token con report:view>" http://localhost:8081/api/products/stats` |
| Resultado esperado | `200 OK` con conteos de activos/inactivos/bajo mínimo y valor total del inventario |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `ProductApiTest` |

## Módulo: Stock (RF-20 a RF-27)

### TC-STOCK-01 — Registrar entrada

| Campo | Detalle |
|---|---|
| Precondición | Login como `admin@test.com` o `warehouse@test.com` |
| Pasos | 1. Ir a `/stock`. 2. Tipo "Entrada", seleccionar producto, cantidad 5. 3. Enviar. |
| Resultado esperado | La cantidad del producto sube en 5; aparece en el historial con `previousQuantity → newQuantity` correctos |
| Estado | ✅ Ejecutado — ver evidencia en [`user-manual.md`](../user-manual.md#registrar-un-movimiento-de-stock) |

### TC-STOCK-02 — Registrar salida con stock suficiente

| Campo | Detalle |
|---|---|
| Pasos | 1. Tipo "Salida", cantidad menor a la disponible. 2. Enviar. |
| Resultado esperado | La cantidad baja correctamente; movimiento con signo negativo en el historial |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `StockPage.test.jsx`/`StockApiTest` |

### TC-STOCK-03 — Salida con stock insuficiente

| Campo | Detalle |
|---|---|
| Pasos | 1. Tipo "Salida", cantidad mayor a la disponible. 2. Enviar. |
| Resultado esperado | `422 Unprocessable Entity`; mensaje de error visible en la UI, sin cambiar la cantidad |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `StockServiceTest`/`StockApiTest`/`stock.spec.js` |

### TC-STOCK-04 — Ajuste de stock

| Campo | Detalle |
|---|---|
| Pasos | 1. Tipo "Ajuste". 2. Ingresar una nueva cantidad absoluta. 3. Enviar. |
| Resultado esperado | `quantity` del producto pasa a ser exactamente el valor ingresado |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `StockServiceTest` |

### TC-STOCK-05 — Movimiento sobre producto inexistente

| Campo | Detalle |
|---|---|
| Pasos | `POST /api/stock/entry` con un `productId` que no existe |
| Resultado esperado | `404 Not Found` |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `StockControllerTest` |

### TC-STOCK-06 — Movimiento sobre producto inactivo

| Campo | Detalle |
|---|---|
| Precondición | Un producto `INACTIVE` |
| Pasos | Registrar una entrada sobre ese producto |
| Resultado esperado | `409 Conflict` (`ProductInactiveException`) |
| Estado | ⬜ Por ejecutar manualmente — cubierto por `StockServiceTest` |

### TC-STOCK-07 — Alerta de stock bajo tras un movimiento

| Campo | Detalle |
|---|---|
| Pasos | 1. Ajustar un producto a una cantidad menor a su `minStock`. 2. Ir a la sección "Alertas" de `/stock`. |
| Resultado esperado | El producto aparece listado en alertas de stock bajo |
| Estado | ✅ Ejecutado — confirmado durante la implementación de FRONT-005 (ver `CLAUDE.md`) |

### TC-STOCK-08 — Historial paginado

| Campo | Detalle |
|---|---|
| Pasos | 1. Ir a `/stock`, sección "Historial". 2. Click "Siguiente". |
| Resultado esperado | La página avanza mostrando movimientos más antiguos, ordenados por fecha descendente |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `StockPage.test.jsx` |

## Módulo: Dashboard (RF-30 a RF-34)

### TC-DASH-01 — KPIs del resumen

| Campo | Detalle |
|---|---|
| Pasos | 1. Login. 2. Ir a `/dashboard`. |
| Resultado esperado | Se muestran 6 KPIs (activos, inactivos, bajo mínimo, valor total, movimientos, etc.) con valores reales |
| Estado | ✅ Ejecutado — ver evidencia en [`user-manual.md`](../user-manual.md#panel-principal-dashboard) |

### TC-DASH-02 — Productos en alerta

| Campo | Detalle |
|---|---|
| Pasos | Con al menos 1 producto bajo su mínimo, revisar la sección "Productos en alerta" del dashboard |
| Resultado esperado | Aparece listado con SKU/nombre/cantidad/stock mínimo |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `DashboardPage.test.jsx` |

### TC-DASH-03 — Movimientos recientes

| Campo | Detalle |
|---|---|
| Pasos | Revisar "Movimientos recientes" tras registrar una entrada |
| Resultado esperado | El movimiento nuevo aparece primero en la lista (máx. 10) |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `dashboard.spec.js` |

### TC-DASH-04 — Top productos (30 días)

| Campo | Detalle |
|---|---|
| Pasos | Revisar "Más movidos (últimos 30 días)" |
| Resultado esperado | Ordenado por cantidad de movimientos, descendente |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `DashboardServiceTest` |

### TC-DASH-05 — Reporte consolidado

| Campo | Detalle |
|---|---|
| Pasos | `curl -H "Authorization: Bearer <token>" http://localhost:8081/api/reports/inventory` |
| Resultado esperado | `200 OK`, JSON con `summary`, `criticalProducts`, `recentMovements`, `topProducts` |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `ReportServiceTest`/`ReportControllerTest` |

## Módulo: Auditoría (RF-40 a RF-42)

### TC-AUDIT-01 — Historial de revisiones de un producto

| Campo | Detalle |
|---|---|
| Precondición | Un producto creado y luego editado al menos una vez, usuario con scope `audit:view` |
| Pasos | `GET /api/audit/products/{id}/revisions` |
| Resultado esperado | Array con una revisión `ADD` y una `MOD`, cada una con `revisedBy` (el usuario real que hizo el cambio) |
| Estado | ⬜ Por ejecutar manualmente — automatizado en `AuditServiceIntegrationTest`/`AuditControllerTest` |

## Ejecución real de un caso durante esta sesión

Como evidencia de que estos casos son realmente ejecutables (no solo texto
plausible), se corrió `TC-AUTH-03` contra el stack real al redactar este
documento:

```
$ curl -i -H "Authorization: Bearer token-invalido" http://localhost:8081/api/products
HTTP/1.1 401
WWW-Authenticate: Bearer error="invalid_token", ...
```

Resultado: `401` confirmado, tal como especifica el caso. El resto de casos
marcados `✅ Ejecutado` se corrieron durante la captura de evidencia de
[`docs/user-manual.md`](../user-manual.md) (mismo stack, mismos usuarios de
prueba) — ver también [`qa-evidence.md`](qa-evidence.md) para el resumen
consolidado de toda la evidencia de QA del proyecto.
