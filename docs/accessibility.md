# Accesibilidad — Auditoría Lighthouse (FRONT-008)

> Auditoría de accesibilidad ejecutada con Lighthouse real (no simulado) contra las 7
> páginas principales de la aplicación, incluyendo las que requieren sesión autenticada.
> Script: [`frontend/scripts/lighthouse-audit.mjs`](../frontend/scripts/lighthouse-audit.mjs).

## Cómo se ejecutó

Lighthouse necesita controlar la navegación completa de la página para medir con
precisión, así que no puede simplemente auditar una página que Playwright ya haya
navegado. Se usó `chromium.launchPersistentContext()` de Playwright (perfil de Chrome
real, no un `browser.newContext()` aislado) con `--remote-debugging-port=9222`: Lighthouse
se conecta por su propia vía CDP a ese mismo puerto y hereda la sesión de Keycloak que
Playwright ya autenticó — sin este mecanismo, Lighthouse solo vería la pantalla de login
en cada página protegida.

```bash
cd frontend
npm run dev            # o BASE_URL=... contra staging
node scripts/lighthouse-audit.mjs
```

Genera reportes HTML/JSON en `frontend/lighthouse-reports/` (gitignored).

## Resultado — línea base (antes de corregir)

Auditado el 2026-07-19 contra `docker-compose.dev.yml` real, logueado como
`admin@test.com`:

| Página | Ruta | Score | Hallazgos |
|--------|------|-------|-----------|
| login | `/` | 100 | 1 — `label-content-name-mismatch` |
| dashboard | `/dashboard` | 100 | 1 — `label-content-name-mismatch` |
| products | `/products` | 91 | 2 — `color-contrast`, `target-size` |
| product-form | `/products/new` | 100 | 0 |
| stock | `/stock` | 100 | 0 |
| audit | `/audit` | 100 | 0 |
| reports | `/reports` | 100 | 0 |

Las 7 páginas ya superaban el umbral de ≥ 90 exigido por el ticket, pero el ticket pide
explícitamente corregir los hallazgos reales en vez de conformarse con el score — los 3
hallazgos (1 compartido entre login/dashboard, 2 propios de products) eran defectos
reales de WCAG, no ruido.

## Hallazgo 1 — `label-content-name-mismatch` (WCAG 2.5.3, Label in Name)

**Dónde:** `QuickAccessCard` (dashboard, dos tarjetas "Productos"/"Stock", visibles
también en `/` tras redirigir a `/dashboard` si ya hay sesión).

**Causa:** el `<Link>` tenía `aria-label={"Ir a " + title}`, que **reemplazaba** el
nombre accesible en vez de contenerlo — axe-core exige que el nombre accesible computado
contenga el texto visible completo (título + descripción), y "Ir a Productos" no contiene
el texto visible "Productos Ver, buscar y gestionar el catálogo de productos" (título +
párrafo de descripción).

**Fix:** se quitó el `aria-label`, dejando que el navegador calcule el nombre accesible a
partir del contenido visible real. Un enlace homónimo en el `Sidebar` (nav) con un nombre
accesible más corto no es en sí una violación de WCAG — son landmarks distintos.

Archivo: [`frontend/src/components/dashboard/QuickAccessCard.jsx`](../frontend/src/components/dashboard/QuickAccessCard.jsx).

## Hallazgo 2 — `color-contrast` (WCAG 1.4.3, contraste mínimo 4.5:1)

**Dónde:** `.badgeWarning` ("Stock bajo") en la tabla de productos.

**Causa:** `color: var(--color-warning)` (`#C55A11`) sobre el fondo tintado
`rgba(197, 90, 17, 0.12)` (compuesto ≈ `#F6E1CD`) da una razón de contraste de **3.42:1**
— confirmado con la fórmula de luminancia relativa de WCAG, calculada a mano y
cross-validada contra el 3.42 exacto que reportó Lighthouse para el color original.

**Fix:** se oscureció el token de diseño `--color-warning` de `#C55A11` a `#963F0A`
(`frontend/src/styles/variables.css`) — nueva razón de contraste **≈ 5.51:1**, con margen
cómodo sobre el mínimo de 4.5:1. Se corrigió en el token global (no solo en
`products.module.css`) porque el mismo par fondo/texto insuficiente se repetía en
`productDetail.module.css` (`.badge` de "Stock bajo" en el detalle de producto) y en
`.lowStock` (texto de advertencia sobre fondo blanco) — cambiar la variable resuelve las
3 instancias de una sola vez, sin duplicar el fix. Los usos de `--color-warning` como
`background-color` (segmentos de gráfico en el dashboard, OBS-004) no tienen requisito de
contraste de texto — siguen siendo decorativos, solo un tono de naranja ligeramente más
oscuro.

Archivo: [`frontend/src/styles/variables.css`](../frontend/src/styles/variables.css).

## Hallazgo 3 — `target-size` (WCAG 2.5.8, tamaño de objetivo mínimo 24×24px)

**Dónde:** enlaces "Ver"/"Editar" (`.actionLink`) y botón "Eliminar" (`.deleteButton`) en
la columna de acciones de la tabla de productos.

**Causa:** eran texto/botón sin padding, con áreas clicables medidas de 20×19px (Ver),
35×19px (Editar) y 64×23px (Eliminar) — las 3 por debajo del mínimo de 24×24px, además de
espacio insuficiente respecto a sus vecinos inmediatos.

**Fix:** se agregó `padding: var(--spacing-sm)` (8px) y `min-height: 24px` a
`.actionLink`/`.deleteButton`, con `display: inline-flex; align-items: center`. El
padding aumenta tanto el alto como el ancho del área clicable muy por encima de 24×24px, y
al ser parte de la caja del elemento (no solo `margin`), también amplía la separación real
entre los tres controles adyacentes.

Archivo: [`frontend/src/styles/products.module.css`](../frontend/src/styles/products.module.css).

## Resultado — después de corregir

Re-auditado contra el mismo stack real tras aplicar los 3 fixes:

| Página | Ruta | Score | Hallazgos |
|--------|------|-------|-----------|
| login | `/` | 100 | 0 |
| dashboard | `/dashboard` | 100 | 0 |
| products | `/products` | 100 | 0 |
| product-form | `/products/new` | 100 | 0 |
| stock | `/stock` | 100 | 0 |
| audit | `/audit` | 100 | 0 |
| reports | `/reports` | 100 | 0 |

**Las 7 páginas alcanzan 100/100 de accesibilidad, sin ningún hallazgo real pendiente.**
No quedó ningún hallazgo diferido/aceptado que documentar.

## Regresión verificada

- `npm run test` (Vitest) → 74/74 OK, sin regresiones (incluye el fix de
  `DashboardPage.test.jsx`/`ProductsPage.test.jsx` para el nuevo nombre accesible de
  `QuickAccessCard`).
- `npx playwright test tests/e2e/dashboard.spec.js tests/e2e/navigation.spec.js
  tests/e2e/products.spec.js` (Chromium) → 11/11 OK contra el stack real.
- `npm run build` → build de producción exitoso, sin errores.
