# Backlog de seguimiento — Auditoría final contra la consigna original

> Formalizado el 2026-07-18 a partir de una auditoría completa del proyecto contra los
> 39 tickets originales de `backlog-inventario.pdf` (no solo el backlog de seguimiento de
> TEST-008, ya cerrado). La auditoría verificó cada EPIC contra el código real — no solo
> contra lo documentado en `CLAUDE.md` — y encontró 5 gaps reales que ningún ticket
> anterior cierra. Detalle completo de cada uno abajo.

| Ticket | Título | EPIC | Responsable | SP | Prioridad |
|--------|--------|------|-------------|-----|-----------|
| FRONT-007 | Implementar AuditPage y ReportsPage (consumir BACK-007/BACK-008) | Frontend | Persona A | 8 | Alta |
| INFRA-006 | Completar gobernanza de repositorio (commitlint, husky, plantillas) | Infraestructura | Persona A | 2 | Media |
| FRONT-008 | Auditoría de accesibilidad con Lighthouse (≥ 90) y remediación | Frontend | Persona B | 3 | Media |
| FRONT-009 | Componentes comunes reutilizables (ConfirmDialog, Skeleton, Toast) | Frontend | Persona B | 5 | Baja |
| FRONT-010 | Filtros de precio/status y ordenamiento por columna en productos | Frontend | Persona A | 2 | Baja |

**Total agregado: 5 tickets · 20 SP**

---

## FRONT-007 — Implementar AuditPage y ReportsPage (consumir BACK-007/BACK-008)

| Campo | Valor |
|---|---|
| Responsable | Persona A |
| Story Points | 8 |
| Prioridad | Alta |
| Etiquetas | frontend, auditoria, reportes, hallazgo-auditoria-final |
| Dependencias | BACK-007, BACK-008, FRONT-001 |

**Objetivo:** dar acceso real desde la UI al historial de auditoría de productos
(`BACK-007`) y al reporte consolidado de inventario (`BACK-008`), ambos completos y
probados en el backend desde 2026-07-09/2026-07-10 pero sin ningún consumidor en el
frontend.

**Descripción:** `FRONT-001` exige explícitamente las rutas `/audit` (`AuditPage`) y
`/reports` (`ReportsPage`) en la sección 4 de la consigna, con sus respectivos
`auditService.js`/`AuditPage.jsx` y enlaces en el `Sidebar`. Ninguno de los dos existe:
`grep -rl "audit\|revision" frontend/src` no devuelve resultados, `services/auditService.js`
no existe, y `Sidebar.jsx` solo enlaza a Dashboard/Productos/Stock (3 de las 5 secciones
planeadas). El endpoint `GET /api/audit/products/{id}/revisions` y `GET
/api/reports/inventory` solo son alcanzables hoy vía Swagger UI o `curl` directo — ningún
usuario real de la aplicación puede consultarlos.

**Contexto de negocio:** la auditoría de cambios es un requisito explícito de la consigna
(Hibernate Envers, BACK-007) y su valor real depende de que alguien pueda consultarla sin
tocar la API directamente. Mismo caso para el reporte consolidado de inventario. Severidad
**Alta** — es la funcionalidad más grande del proyecto original con cero superficie de
usuario.

**Alcance técnico:**
- `pages/AuditPage.jsx`: buscador/selector de producto (reutilizar el patrón de
  `ProductFilters`) + tabla de revisiones (`revisionNumber`, `revisionTimestamp`,
  `revisionType`, `revisedBy`, snapshot de campos) consumiendo
  `GET /api/audit/products/{id}/revisions` (scope `audit:view`).
- `pages/ReportsPage.jsx`: vista del reporte consolidado (`GET /api/reports/inventory`,
  scope `report:view`) — reutiliza los mismos componentes de KPI/gráfico ya construidos
  en `FRONT-002` (ampliación) en vez de duplicarlos.
- `services/auditService.js` y `services/reportService.js` (nuevos, siguiendo el patrón
  de `productService.js`/`dashboardService.js`).
- `hooks/useAudit.js` (nuevo, `useQuery` sobre `getProductRevisions(productId)`,
  `enabled: Boolean(productId)`).
- `routes/AppRoutes.jsx`: agregar `/audit` y `/reports`, anidadas bajo
  `<AppShell />`/`<ProtectedRoute />` igual que el resto.
- `components/layout/Sidebar.jsx`: agregar los 2 enlaces faltantes (`Auditoría`,
  `Reportes`).
- Punto de entrada a `AuditPage` desde `ProductDetailPage`/`ProductsTable` (ej. enlace
  "Ver historial" por producto) — sin esto, `AuditPage` solo sería alcanzable escribiendo
  la URL a mano.

**Pasos de implementación:**
1. Crear `services/auditService.js` (`getProductRevisions(productId)`) y
   `services/reportService.js` (`getInventoryReport()`).
2. Crear `hooks/useAudit.js` y reutilizar/extender `hooks/useDashboard.js` si aplica para
   el reporte.
3. Crear `components/audit/RevisionsTable.jsx` (tabla de revisiones con badge de tipo
   ADD/MOD/DEL).
4. Crear `pages/AuditPage.jsx` con selector de producto + `RevisionsTable`.
5. Crear `pages/ReportsPage.jsx` reutilizando `KpiCard`/`StockStatusChart` de FRONT-002.
6. Agregar las 2 rutas en `AppRoutes.jsx` y los 2 enlaces en `Sidebar.jsx`.
7. Agregar enlace "Ver historial" en `ProductDetailPage`/`ProductsTable` hacia
   `/audit?productId=...` (o `/products/:id/audit`, a definir).
8. Tests unitarios de `AuditPage`/`ReportsPage` (mock de servicios, patrón ya establecido
   en `ProductsPage.test.jsx`).
9. Verificar con Playwright contra el stack real: crear/editar un producto, confirmar que
   las revisiones aparecen en `AuditPage` con el usuario y timestamp correctos.

**Validaciones:** `AuditPage` muestra el historial real de un producto tras crearlo y
editarlo; `ReportsPage` muestra datos consistentes con `DashboardPage`; ambas rutas
respetan sus scopes (`audit:view`/`report:view` — un usuario sin el scope ve un error
igual que el resto de la app, no una ruta rota).

**Casos de error:** producto sin revisiones (recién creado, antes del primer commit de
Envers) — verificar que no rompe la UI; usuario sin `audit:view`/`report:view` navegando
directo a la URL — debe comportarse igual que el 403 ya manejado en el resto de la app
(ver "Explícitamente diferido" de SEC-001/FRONT-004 sobre `hasScope()` en la UI).

**Pruebas requeridas:** tests unitarios de ambas páginas; E2E nuevo o ampliación de
`navigation.spec.js` verificando que los 2 enlaces nuevos del `Sidebar` navegan
correctamente; regresión de la suite completa de frontend.

**Criterios de aceptación:** un usuario con los scopes correctos puede, desde la UI (sin
tocar Swagger/curl), ver el historial de auditoría de cualquier producto y el reporte
consolidado del inventario.

**Definición de Done:** `AuditPage`/`ReportsPage` commiteadas, rutas y enlaces del
`Sidebar` funcionando, tests nuevos pasando, verificado contra el stack real, PR
aprobado.

---

## INFRA-006 — Completar gobernanza de repositorio (commitlint, husky, plantillas)

| Campo | Valor |
|---|---|
| Responsable | Persona A |
| Story Points | 2 |
| Prioridad | Media |
| Etiquetas | infra, git, governance, hallazgo-auditoria-final |
| Dependencias | INFRA-001 |

**Objetivo:** cerrar el alcance técnico completo de `INFRA-001` (Configuración inicial
del repositorio), que quedó parcialmente implementado — `CONTRIBUTING.md` existe, pero
faltan las herramientas que hacen cumplir las convenciones automáticamente.

**Descripción:** el ticket original `INFRA-001` pedía explícitamente: `.editorconfig`,
`commitlint` + `husky` (para rechazar commits que no sigan Conventional Commits),
`.github/PULL_REQUEST_TEMPLATE.md`, y `.github/ISSUE_TEMPLATE/` con `bug_report.md` y
`feature_request.md`. Verificado contra el repo real: ninguno de estos 5 archivos existe
(`.github/ISSUE_TEMPLATE/` solo tiene un `.gitkeep`). El proyecto sí sigue Conventional
Commits en la práctica (verificable en el historial de `git log`), pero nada lo obliga
automáticamente — depende enteramente de la disciplina manual.

**Contexto de negocio:** la consigna evalúa explícitamente "commitlint rechaza commits no
convencionales" y "plantillas de PR e Issue funcionan correctamente" como Validaciones y
Evidencias Esperadas de `INFRA-001`. Sin esto, un commit que no siga el formato pasa sin
ninguna advertencia. Severidad **Media** — no bloquea funcionalidad, pero es un
entregable explícito de la consigna que el docente puede verificar directamente.

**Alcance técnico:**
- `.editorconfig` en la raíz (indentación, charset, final newline — consistente entre
  Java/JS).
- `commitlint.config.js` (`@commitlint/config-conventional`) + `husky` (`commit-msg` hook).
- `.github/PULL_REQUEST_TEMPLATE.md` (secciones: Descripción, Cambios, Evidencias,
  Checklist — mismo formato que ya usan los PRs reales de este proyecto en su texto
  sugerido, solo falta formalizarlo como plantilla).
- `.github/ISSUE_TEMPLATE/bug_report.md` y `feature_request.md`.

**Pasos de implementación:**
1. Crear `.editorconfig` (reglas estándar para `.java`/`.js`/`.jsx`/`.yml`/`.md`).
2. Instalar `husky` + `@commitlint/cli` + `@commitlint/config-conventional` en un
   `package.json` de raíz (nuevo — hoy solo existen `backend/`/`frontend/` con sus propios
   `package.json`/`build.gradle.kts`; para que `husky` funcione en todo el repo hace
   falta un `package.json` en la raíz, aunque sea mínimo, con `husky` como única
   dependencia real).
3. Crear `commitlint.config.js` extendiendo `@commitlint/config-conventional`.
4. Configurar el hook `commit-msg` de husky para correr `commitlint --edit`.
5. Crear `.github/PULL_REQUEST_TEMPLATE.md`.
6. Crear `.github/ISSUE_TEMPLATE/bug_report.md` y `feature_request.md`.
7. Probar: un commit con mensaje no convencional (ej. `"arreglos varios"`) debe ser
   rechazado localmente por el hook.

**Validaciones:** commit no convencional rechazado por el hook local; PR nuevo muestra la
plantilla automáticamente; Issue nuevo ofrece elegir entre bug/feature.

**Casos de error:** el hook de husky no corre en un clon fresco si no se ejecuta
`npm install` en la raíz primero (`prepare` script de `husky` debe encargarse de esto
automáticamente) — documentar en `CONTRIBUTING.md`.

**Pruebas requeridas:** verificar manualmente que un commit mal formado sea rechazado;
verificar que un PR/Issue nuevo en GitHub muestra las plantillas.

**Criterios de aceptación:** los 5 archivos existen y funcionan como se describe en el
alcance técnico de `INFRA-001` original.

**Definición de Done:** archivos commiteados, hook probado localmente, PR aprobado.

---

## FRONT-008 — Auditoría de accesibilidad con Lighthouse (≥ 90) y remediación

| Campo | Valor |
|---|---|
| Responsable | Persona B |
| Story Points | 3 |
| Prioridad | Media |
| Etiquetas | frontend, accesibilidad, lighthouse, hallazgo-auditoria-final |
| Dependencias | FRONT-001, FRONT-002, FRONT-003, FRONT-005 |

**Objetivo:** ejecutar Lighthouse contra la aplicación real y corregir lo necesario para
alcanzar el score de accesibilidad ≥ 90 que exige la sección 2 (stack obligatorio) y
`FRONT-001` (criterio de validación explícito) de la consigna.

**Descripción:** Lighthouse aparece listado como herramienta obligatoria del stack
("validación de accesibilidad >= 90") y como Validación/Evidencia Esperada explícita de
`FRONT-001`. Verificado en `CLAUDE.md`: aparece mencionado únicamente en la lista de
comandos (sección 16) y anotado como `⬜ Pendiente`/diferido en `FRONT-001` y `SEC-003` —
nunca se ejecutó realmente contra la app en ninguna sesión de este proyecto.

**Contexto de negocio:** es un entregable explícito con un umbral numérico verificable
por el docente (`npx lighthouse http://localhost:5173 --view`). Severidad **Media** — no
es una vulnerabilidad ni un defecto funcional, pero es una Evidencia Esperada nunca
producida.

**Alcance técnico:**
- Ejecutar Lighthouse (CLI o DevTools) contra las páginas principales ya autenticadas:
  `LoginPage`, `DashboardPage`, `ProductsPage`, `ProductFormPage`, `StockPage`, y las
  nuevas `AuditPage`/`ReportsPage` si `FRONT-007` ya está mergeado.
- Corregir los hallazgos reales que bloqueen el score ≥ 90 (contraste de color, `alt` en
  imágenes/íconos, roles ARIA faltantes, orden de foco, labels de formulario).
- Documentar el resultado (antes/después) en `docs/`.

**Pasos de implementación:**
1. `npx lighthouse http://localhost:5173/dashboard --view` (con sesión autenticada real,
   no la pantalla de login sola) contra el stack real corriendo.
2. Repetir para `/products`, `/products/new`, `/stock`, y las páginas nuevas de FRONT-007.
3. Listar cada hallazgo de accesibilidad con su severidad.
4. Corregir hallazgos reales (no forzar el score sin justificación — documentar cualquier
   hallazgo que se decida no corregir y por qué).
5. Re-ejecutar Lighthouse tras cada corrección hasta alcanzar ≥ 90 en cada página.
6. Documentar resultados finales en `docs/accessibility.md` (nuevo) con capturas/scores
   reales.

**Validaciones:** score de accesibilidad ≥ 90 en las páginas principales, verificado con
Lighthouse real, no estimado.

**Casos de error:** un componente de terceros (ej. widget de Keycloak en `silent-check-sso.html`)
puede no ser corregible directamente — documentar como limitación conocida si aplica.

**Pruebas requeridas:** reporte de Lighthouse real por página, antes y después de las
correcciones.

**Criterios de aceptación:** ≥ 90 de accesibilidad en Lighthouse para cada página
principal de la aplicación autenticada.

**Definición de Done:** correcciones commiteadas, `docs/accessibility.md` con evidencia
real, PR aprobado.

---

## FRONT-009 — Componentes comunes reutilizables (ConfirmDialog, Skeleton, Toast)

| Campo | Valor |
|---|---|
| Responsable | Persona B |
| Story Points | 5 |
| Prioridad | Baja |
| Etiquetas | frontend, ux, refactor, hallazgo-auditoria-final |
| Dependencias | FRONT-003, FRONT-004, FRONT-005 |

**Objetivo:** reemplazar los mecanismos nativos usados hoy (`window.confirm`, texto
plano de "Cargando...", ausencia de notificaciones) por los componentes reutilizables
`ConfirmDialog`/`Skeleton`/Toast que pedía explícitamente la arquitectura de
`components/common/` en la sección 2/4 de la consigna.

**Descripción:** `components/common/` solo contiene `Pagination.jsx` hoy. La consigna
(sección 2, estructura de carpetas) especifica `Button`, `Input`, `Modal`, `Pagination`,
`ConfirmDialog`, `Skeleton` como componentes comunes, y `FRONT-002`/`FRONT-003`/`FRONT-004`/
`FRONT-005` piden explícitamente "toast de error"/"toast de éxito" y "skeleton loaders"
como parte de sus Alcance Técnico/Pasos de Implementación. El proyecto real usa
`window.confirm()` (FRONT-006 lo confirmó y mejoró el mensaje, pero sigue siendo el
diálogo nativo del navegador, no un componente propio) y mensajes de texto plano
(`<p>Cargando...</p>`) en `DashboardPage`/`ProductsPage`/`StockPage` en vez de skeletons,
y no existe ningún sistema de toast en todo el proyecto.

**Contexto de negocio:** es una desviación arquitectónica consistente en todo el
frontend, no un bug puntual — vale la pena resolverla una sola vez de forma centralizada
en vez de parche por parche. Severidad **Baja** — la app funciona correctamente hoy, esto
es una mejora de UX/adherencia a la arquitectura pedida, no una corrección de un defecto.

**Alcance técnico:**
- `components/common/ConfirmDialog.jsx`: modal accesible (focus trap, `Escape` cierra,
  `aria-modal`) reemplazando `window.confirm()` en `ProductsTable` (FRONT-006).
- `components/common/Skeleton.jsx`: bloques de carga reutilizables, reemplazando los
  mensajes de texto plano en `DashboardPage`/`ProductsPage`/`StockPage`.
- `components/common/Toast.jsx` + un `ToastContext`/hook `useToast()`: notificaciones de
  éxito/error no bloqueantes, para los flujos de creación/edición/eliminación
  (`ProductFormPage`, `StockPage`, `ProductsTable`).

**Pasos de implementación:**
1. Crear `ConfirmDialog.jsx` (props: `message`, `onConfirm`, `onCancel`, `open`).
2. Reemplazar `window.confirm()` en `ProductsTable.handleDelete` por `ConfirmDialog`,
   preservando el mensaje condicional de stock ya implementado en FRONT-006.
3. Crear `Skeleton.jsx` (variantes: fila de tabla, tarjeta de KPI, bloque de texto).
4. Reemplazar los `isLoading && <p>Cargando...</p>` en `DashboardPage`/`ProductsPage`/
   `StockPage` por `Skeleton`.
5. Crear `ToastContext.jsx` + `useToast()` + `Toast.jsx` (auto-dismiss, `aria-live`).
6. Envolver la app en `ToastProvider` (en `App.jsx`, junto a `QueryClientProvider`).
7. Disparar toasts de éxito/error en `ProductFormPage` (crear/editar), `StockPage`
   (registrar movimiento), `ProductsTable` (eliminar).
8. Actualizar los tests existentes que dependían de `window.confirm`/mensajes de texto
   plano (`ProductsTable.test.jsx`, `DashboardPage.test.jsx`, etc.) al nuevo
   comportamiento.

**Validaciones:** eliminar un producto muestra `ConfirmDialog` (no el diálogo nativo del
navegador); las páginas con carga muestran skeletons; crear/editar/eliminar dispara un
toast visible.

**Casos de error:** verificar que el cambio no rompe ningún test E2E existente que
interactúa con los diálogos nativos actuales (`page.on('dialog', ...)` en
`products.spec.js`) — habrá que migrar esos tests al nuevo `ConfirmDialog`.

**Pruebas requeridas:** tests unitarios de los 3 componentes nuevos; actualizar los tests
unitarios/E2E existentes afectados por el cambio; regresión completa de la suite.

**Criterios de aceptación:** los 3 componentes existen, están en uso real reemplazando
los mecanismos nativos, y ningún test existente queda roto.

**Definición de Done:** componentes commiteados, usos migrados, tests actualizados y
pasando, PR aprobado.

---

## FRONT-010 — Filtros de precio/status y ordenamiento por columna en productos

| Campo | Valor |
|---|---|
| Responsable | Persona A |
| Story Points | 2 |
| Prioridad | Baja |
| Etiquetas | frontend, productos, filtros, hallazgo-auditoria-final |
| Dependencias | FRONT-003 |

**Objetivo:** exponer en la UI los filtros de precio (`minPrice`/`maxPrice`) y `status`,
y el ordenamiento por columna, que el backend ya soporta (`ProductController`/
`ProductSpecifications`, sección 8) pero que `ProductFilters`/`ProductsTable` nunca
consumen.

**Descripción:** `BACK-003`/`BACK-004` (ampliación) implementaron el soporte
server-side completo para `?minPrice&maxPrice&status&sort` en `GET /api/products`, ya
verificado y en producción. `FRONT-003` nunca amplió `ProductFilters.jsx`/
`ProductsTable.jsx` para exponerlos — quedó explícitamente anotado como diferido en la
ampliación de FRONT-003 ("Filtros de precio... y de status... no eran requeridos por
ningún avance del docente"). Sigue siendo cierto que no lo exigió el avance parcial, pero
sí es parte del Alcance Técnico original de `FRONT-003` en la consigna completa.

**Contexto de negocio:** funcionalidad de bajo riesgo y alto valor — el backend ya la
soporta por completo, solo falta conectarla. Severidad **Baja**.

**Alcance técnico:**
- `components/products/ProductFilters.jsx`: agregar 2 inputs numéricos (`minPrice`/
  `maxPrice`) y un `<select>` de `status` (`ACTIVE`/`INACTIVE`/todos).
- `components/products/ProductsTable.jsx`: headers de columna clickeables para ordenar
  (`sort=name,asc` / `sort=price,desc` etc.), con indicador visual de la columna/dirección
  activa.
- `hooks/useProducts.js`: `useProducts` ya acepta parámetros arbitrarios — solo hace
  falta pasarle los nuevos desde `ProductsPage`.

**Pasos de implementación:**
1. Agregar los 2 inputs de precio y el select de status a `ProductFilters.jsx`.
2. Propagar `minPrice`/`maxPrice`/`status` como estado en `ProductsPage.jsx` (mismo
   patrón que `category`/`q` ya existentes).
3. Agregar `sort` como estado, con headers de `ProductsTable` clickeables que lo
   actualizan.
4. Resetear a página 1 al cambiar cualquier filtro nuevo (mismo patrón ya usado para
   `category`/`q`/`lowStockOnly`).
5. Tests unitarios nuevos en `ProductsPage.test.jsx` para los filtros/orden nuevos.

**Validaciones:** filtrar por rango de precio devuelve solo productos dentro del rango;
filtrar por status funciona; hacer click en un header de columna reordena la tabla.

**Casos de error:** `minPrice > maxPrice` — decidir si se valida en el frontend o se deja
que el backend devuelva una lista vacía (comportamiento actual del backend, razonable sin
cambios).

**Pruebas requeridas:** tests unitarios de `ProductsPage` con los filtros/orden nuevos;
regresión de los tests existentes de `ProductsPage`/`ProductsTable`.

**Criterios de aceptación:** los 3 filtros nuevos (precio, status) y el ordenamiento por
columna funcionan end-to-end contra el backend real.

**Definición de Done:** cambios commiteados, tests nuevos pasando, PR aprobado.
