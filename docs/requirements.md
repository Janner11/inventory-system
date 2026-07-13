# Requisitos del Sistema

Matriz de requisitos funcionales y no funcionales, cada uno trazable a su
implementación real (endpoint, clase, archivo de configuración o
verificación ya ejecutada) — no una lista aspiracional. Convención de ID:
`RF-XX` (funcional) / `RNF-XX` (no funcional). Para el detalle de arquitectura
y decisiones técnicas, ver [`docs/architecture.md`](architecture.md).

## Índice

1. [Requisitos funcionales](#requisitos-funcionales)
   - [Autenticación y autorización](#autenticación-y-autorización)
   - [Gestión de productos](#gestión-de-productos)
   - [Gestión de stock](#gestión-de-stock)
   - [Dashboard y reportes](#dashboard-y-reportes)
   - [Auditoría](#auditoría)
2. [Requisitos no funcionales](#requisitos-no-funcionales)
   - [Seguridad](#seguridad)
   - [Rendimiento](#rendimiento)
   - [Disponibilidad y resiliencia](#disponibilidad-y-resiliencia)
   - [Observabilidad](#observabilidad)
   - [Usabilidad](#usabilidad)
   - [Mantenibilidad y calidad de código](#mantenibilidad-y-calidad-de-código)

## Requisitos funcionales

### Autenticación y autorización

| ID | Requisito | Implementación | Verificación |
|---|---|---|---|
| RF-01 | El sistema debe autenticar usuarios vía OAuth2 Authorization Code + PKCE contra un proveedor de identidad externo | `frontend/src/context/AuthContext.jsx`, `services/keycloak.js`; Keycloak realm `inventario` | `TC-AUTH-01` a `TC-AUTH-04` ([`test-cases.md`](testing/test-cases.md)); E2E `auth.spec.js` |
| RF-02 | Cada request a la API debe portar un JWT Bearer y ser validado contra el emisor real | `backend/.../config/SecurityConfig.java` (`JwtDecoder`), `security/JwtAuthConverter.java` | `SecurityIntegrationTest` (Keycloak real, Testcontainers) |
| RF-03 | El sistema debe autorizar cada endpoint por **scope individual**, nunca por rol genérico | `@PreAuthorize("hasAuthority('SCOPE_...')")` en los 5 controllers | `*ControllerTest` (401/403 por endpoint); `AuthApiTest` |
| RF-04 | Deben existir al menos 5 roles con distintas combinaciones de permisos | `keycloak/realm.json` — `ADMIN`/`MANAGER`/`WAREHOUSE`/`VIEWER`/`AUDITOR` | `TC-AUTH-05`; ver matriz completa en [`docs/security/keycloak.md`](security/keycloak.md) |
| RF-05 | La UI debe ocultar los controles de gestión (crear/editar/eliminar) a usuarios sin el scope correspondiente | `hasScope('product:manage')` en `ProductsPage.jsx`/`ProductsTable.jsx` | `TC-AUTH-06`; E2E `permissions.spec.js` |
| RF-06 | El usuario debe poder cerrar sesión, invalidando el token localmente | `AuthContext.logout()` → `keycloak.logout()` | `TC-AUTH-04` |

### Gestión de productos

| ID | Requisito | Implementación | Verificación |
|---|---|---|---|
| RF-10 | El sistema debe listar productos de forma paginada, con orden configurable | `GET /api/products` (`ProductController`, `ProductService.getAllProducts`) | `TC-PROD-01`; `ProductApiTest` |
| RF-11 | Debe permitir buscar productos por nombre o SKU | `GET /api/products/search?q=` | `TC-PROD-02` |
| RF-12 | Debe permitir filtrar productos por categoría | `GET /api/products?category=` (`ProductSpecifications`) | `TC-PROD-03` |
| RF-13 | Debe exponer los productos cuya cantidad está bajo su stock mínimo | `GET /api/products/critical` | `TC-PROD-04`; usado también por el Dashboard (RF-30) |
| RF-14 | Debe permitir crear un producto con nombre, SKU (único), categoría, precio, cantidad y stock mínimo | `POST /api/products`; `ProductRequestDTO` (`@NotBlank`/`@Size`/`@Positive`/`@Min(0)`); `DuplicateSkuException` → 409 | `TC-PROD-05`, `TC-PROD-06` |
| RF-15 | El SKU debe normalizarse siempre a mayúsculas | `ProductService.createProduct` (`sku.toUpperCase()`) | `TC-PROD-05` |
| RF-16 | Debe permitir editar un producto existente, revalidando la unicidad del SKU | `PUT /api/products/{id}` | `TC-PROD-07` |
| RF-17 | Eliminar un producto debe ser un *soft delete* (`status=INACTIVE`), nunca borrar la fila | `DELETE /api/products/{id}` (ADR-001) — preserva la integridad referencial con `stock_movements` | `TC-PROD-08` |
| RF-18 | Debe existir un formulario de creación/edición con validación en el cliente antes de llamar a la API | `ProductForm.jsx` (React Hook Form) | `TC-PROD-05`, `TC-PROD-09` |
| RF-19 | Debe exponer estadísticas agregadas del catálogo (activos, inactivos, bajo mínimo, valor total) | `GET /api/products/stats` | `TC-PROD-10` |

### Gestión de stock

| ID | Requisito | Implementación | Verificación |
|---|---|---|---|
| RF-20 | Debe permitir registrar una **entrada** de stock, incrementando la cantidad | `POST /api/stock/entry`; `StockService.registerEntry` | `TC-STOCK-01` |
| RF-21 | Debe permitir registrar una **salida** de stock, decrementando la cantidad, rechazando si excede el disponible | `POST /api/stock/exit` → `InsufficientStockException` (422) | `TC-STOCK-02`, `TC-STOCK-03` |
| RF-22 | Debe permitir un **ajuste** de stock a un valor absoluto (ej. tras un conteo físico) | `POST /api/stock/adjust`; `StockAdjustmentRequestDTO` | `TC-STOCK-04` |
| RF-23 | Cada movimiento debe registrar cantidad previa, nueva cantidad, tipo, motivo, observaciones y quién lo realizó | `StockMovement` entity + `StockMovementRequestDTO`/`StockAdjustmentRequestDTO` (`performedBy` `@NotBlank`) | `TC-STOCK-01` a `TC-STOCK-04` |
| RF-24 | Un movimiento sobre un producto inexistente o inactivo debe rechazarse | `findActiveProductOrThrow` → 404 / `ProductInactiveException` → 409 | `TC-STOCK-05`, `TC-STOCK-06` |
| RF-25 | El sistema debe alertar (log + endpoint dedicado) cuando `quantity < minStock` tras un movimiento | `StockService` (`log.warn`); `GET /api/stock/alerts` | `TC-STOCK-07` |
| RF-26 | Debe existir un historial de movimientos paginado, global y por producto | `GET /api/stock/movements`, `GET /api/stock/movements/{productId}` | `TC-STOCK-08` |
| RF-27 | Bajo concurrencia, el sistema no debe permitir que el stock quede negativo (sobreventa) | Optimistic locking (`@Version` en `Product`) | Verificado con 5 requests simultáneas reales (Charter 3, [`exploratory-testing-report.md`](testing/exploratory-testing-report.md)) |

### Dashboard y reportes

| ID | Requisito | Implementación | Verificación |
|---|---|---|---|
| RF-30 | El dashboard debe mostrar KPIs del inventario (activos, inactivos, bajo mínimo, valor total, movimientos) | `GET /api/dashboard/summary`; `KpiCard` × 6 | `TC-DASH-01` |
| RF-31 | Debe mostrar los productos en alerta de stock bajo | `GET /api/dashboard/critical-products`; `CriticalProductsTable` | `TC-DASH-02` |
| RF-32 | Debe mostrar los últimos 10 movimientos de stock | `GET /api/dashboard/recent-movements`; `RecentMovementsWidget` | `TC-DASH-03` |
| RF-33 | Debe mostrar los productos más movidos en los últimos 30 días | `GET /api/dashboard/top-products`; `TopProductsWidget` | `TC-DASH-04` |
| RF-34 | Debe existir un reporte consolidado de inventario en un solo endpoint | `GET /api/reports/inventory` | `TC-DASH-05` |

### Auditoría

| ID | Requisito | Implementación | Verificación |
|---|---|---|---|
| RF-40 | Todo cambio (creación/edición/eliminación) sobre un producto debe quedar auditado | Hibernate Envers (`@Audited` en `Product`), tablas `products_aud`/`revinfo` | `ProductAuditIntegrationTest` |
| RF-41 | Cada revisión debe registrar **quién** la realizó, no solo cuándo | `AuditRevisionEntity`/`AuditRevisionListener` (`revinfo.username`) | `AuditServiceIntegrationTest` |
| RF-42 | Debe existir un endpoint para consultar el historial de revisiones de un producto | `GET /api/audit/products/{id}/revisions` | `TC-AUDIT-01` |

## Requisitos no funcionales

### Seguridad

| ID | Requisito | Estado verificado |
|---|---|---|
| RNF-01 | 0 vulnerabilidades HIGH en el escaneo dinámico (OWASP ZAP) de frontend y backend | ✅ `FAIL-NEW: 0` en ambos scans (TEST-005) |
| RNF-02 | 0 vulnerabilidades CVSS ≥ 9.0 (CRITICAL) en dependencias de aplicación (`runtimeClasspath`) | ✅ OWASP Dependency-Check, 0 encontradas |
| RNF-03 | 0 vulnerabilidades CRITICAL sin justificar en las imágenes Docker finales | ✅ Trivy, gate real en CI/Jenkins; 1 CRITICAL real sin fix disponible (`CVE-2026-22732`, Spring Security) documentado como riesgo aceptado en `backend/.trivyignore` (ver "Detalle de CICD-004" en `CLAUDE.md`) |
| RNF-04 | El JWT nunca debe persistirse en `localStorage` | ✅ `keycloak-js` lo mantiene en memoria (verificado, Charter 1 exploratorio) |
| RNF-05 | Las contraseñas/secrets no deben estar hardcodeados en el código versionado | ✅ Todo vía variables de entorno (`.env.example`, sin defaults para credenciales "reales" en staging) |
| RNF-06 | El sistema debe resistir ataques clásicos de JWT (`alg:none`, firma vacía, confusión de algoritmo) | ✅ Verificado manualmente (Charter 1) — los 3 rechazados con 401 |

### Rendimiento

| ID | Requisito | Estado verificado |
|---|---|---|
| RNF-10 | p95 de latencia HTTP < 500ms bajo carga normal (50 VU) | ✅ **19.55ms** medido (load test, 25× margen) |
| RNF-11 | Tasa de error < 1% bajo carga normal | ✅ **0.40%** medido |
| RNF-12 | Throughput > 100 req/s bajo carga normal | ✅ **128.98 req/s** medido |
| RNF-13 | El sistema debe degradarse de forma predecible bajo carga extrema, sin caerse | ✅ Verificado (stress test) — degradación gradual desde ~100-150 VU, causa raíz identificada (pool HikariCP), sin caídas del servicio |
| RNF-14 | Sin fugas de memoria bajo carga sostenida | ✅ Soak test 30 min, tasa de GC estable — sin evidencia de fuga |

Detalle completo, metodología y gráficas de las 3 corridas en
[`docs/performance.md`](performance.md).

### Disponibilidad y resiliencia

| ID | Requisito | Implementación |
|---|---|---|
| RNF-20 | Cada contenedor de aplicación debe exponer un `HEALTHCHECK` de Docker | `backend/Dockerfile`, `frontend/Dockerfile` (`wget --spider .../actuator/health` / `/healthz`) |
| RNF-21 | El arranque completo del entorno (staging) debe completarse en menos de 3 minutos | ✅ **48 segundos** medido (INFRA-004) |
| RNF-22 | Las migraciones de base de datos deben ser inmutables una vez aplicadas | ADR-005 — Flyway valida checksums |
| RNF-23 | El backend debe seguir sirviendo tráfico si un componente de observabilidad (Alloy/Prometheus) no está disponible | Instrumentación vía agente asíncrono (OTel Java Agent) — no bloquea el request path |

### Observabilidad

| ID | Requisito | Implementación |
|---|---|---|
| RNF-30 | Toda petición debe generar métricas, logs y trazas correlacionables por `traceId` | OTel Java Agent → Grafana Alloy → Prometheus/Loki/Tempo (OBS-001) |
| RNF-31 | Deben existir alertas automáticas ante degradación (CPU, errores, latencia, caída de servicio, fallos de auth) | 5 reglas en Prometheus/Alertmanager (OBS-005), verificadas con disparo real |
| RNF-32 | Debe existir un dashboard visual por dominio (aplicación, infraestructura, negocio, seguridad) | 4 dashboards de Grafana provisionados como código (OBS-004) |

### Usabilidad

| ID | Requisito | Implementación |
|---|---|---|
| RNF-40 | La interfaz debe ser responsive en móvil (390px), tablet y escritorio | CSS puro con 3 breakpoints (`variables.css`); verificado con Playwright a 390px (`responsive.spec.js`) |
| RNF-41 | Los formularios deben mostrar errores de validación claros antes de llamar a la API | React Hook Form + reglas espejo de las del backend (`ProductForm`, `StockMovementForm`) |
| RNF-42 | La navegación debe indicar la sección activa y la ruta actual | `NavLink`/`aria-current="page"` (Sidebar) + `Breadcrumb` |

### Mantenibilidad y calidad de código

| ID | Requisito | Estado verificado |
|---|---|---|
| RNF-50 | Cobertura de líneas ≥ 70% (objetivo general), ≥ 85% en los servicios de negocio críticos | ✅ 100% líneas en `ProductService`/`StockService`; gate real vía `jacocoTestCoverageVerification` |
| RNF-51 | 0 bugs/vulnerabilidades nuevos según el Quality Gate de SonarQube | ✅ "Inventario Quality Gate" verificado en verde con análisis reales (CICD-003) |
| RNF-52 | Duplicación de código ≤ 3% | ✅ Umbral del Quality Gate, cumplido en la última verificación |
| RNF-53 | Todas las migraciones y la lógica de negocio deben tener tests automatizados ejecutándose en cada PR | ✅ 248 tests de backend + 62 de frontend, corridos en `ci.yml`/`Jenkinsfile` en cada push/PR |

## Trazabilidad — cómo se verificó

Cada fila de este documento se contrastó contra el código real (no contra la
memoria de sesiones anteriores) al momento de escribirlo: los endpoints se
extrajeron con `grep` sobre los controllers reales, las anotaciones de
validación se leyeron directamente de los DTOs, y las cifras de rendimiento/
cobertura/seguridad son las últimas medidas reales documentadas en
[`docs/performance.md`](performance.md), [`docs/testing/testing-strategy.md`](testing/testing-strategy.md)
y `CLAUDE.md`. Ningún requisito de este documento describe una aspiración
sin verificar.
