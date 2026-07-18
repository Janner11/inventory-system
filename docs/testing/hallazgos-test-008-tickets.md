# Tickets de seguimiento — Hallazgos de TEST-008

TEST-008 (exploratory testing manual, `docs/testing/exploratory-testing-report.md`)
encontró 5 bugs reales y dejó documentada su recomendación, pero el ticket en sí
pedía **explorar y documentar, no corregir**. Este documento formaliza esa
recomendación como 6 tickets de backlog listos para trabajarse — los 5 bugs de
TEST-008 más un gap de infraestructura detectado por separado (falta un tercer
ambiente de Production real).

Los 5 bugs de origen ya están descritos con evidencia completa (pasos de
reproducción, logs, severidad) en `exploratory-testing-report.md` — este
documento no repite esa evidencia, solo la convierte en tickets accionables.

## Resumen

| ID | Título | EPIC | Responsable | SP | Prioridad |
|---|---|---|---|---|---|
| INFRA-005 | Crear ambiente de Production real y separado | EPIC-1 Infraestructura | Persona A | 3 | Alta |
| SEC-004 | Restringir acceso no autenticado a `/actuator/prometheus` | EPIC-2 Seguridad | Persona B | 3 | Alta |
| SEC-005 | Activar protección contra fuerza bruta en Keycloak | EPIC-2 Seguridad | Persona B | 2 | Alta |
| SEC-006 | Revocar refresh tokens ya rotados en Keycloak | EPIC-2 Seguridad | Persona A | 3 | Alta |
| BACK-009 | Validaciones de rango en `price` y `performedBy` | EPIC-3 Backend | Persona A | 3 | Media |
| FRONT-006 | Advertir stock disponible al desactivar un producto | EPIC-4 Frontend | Persona B | 1 | Baja |

**Total: 6 tickets · 15 SP**

---

## INFRA-005 — Crear ambiente de Production real y separado (3er ambiente obligatorio)

| Campo | Valor |
|---|---|
| Responsable | Persona A |
| Story Points | 3 |
| Prioridad | Alta |
| Etiquetas | infra, docker, produccion, hallazgo-testing-exploratorio |
| Rama sugerida | `fix/infra-production-environment` |
| Dependencias | INFRA-004 |

**Objetivo:** crear un ambiente de Production real y separado, para cumplir con
el requisito obligatorio de la consigna de mantener 3 ambientes (Development,
Preview/Staging, Production) — hoy solo existen los primeros dos.

**Descripción detallada:** crear `docker-compose.production.yml` como un tercer
ambiente, distinto de staging, que represente el entorno final de producción.
Debe reutilizar la misma arquitectura ya validada en staging (INFRA-004) pero
con las diferencias reales que se esperan entre preview y producción: sin datos
de prueba, con configuración de seguridad más estricta, y documentado como el
ambiente "final" del sistema.

**Contexto de negocio:** la consigna del proyecto exige explícitamente 3
ambientes obligatorios. Actualmente el proyecto solo tiene dos
(`docker-compose.dev.yml` y `docker-compose.staging.yml`) —
`docs/deployment.md` ya lo reconoce como una limitación. Es uno de los gaps
más visibles de cara a la evaluación (Funcionalidad 15%, CI/CD 15%,
Documentación 10%).

**Alcance técnico:**
- Nuevo `docker-compose.production.yml` (basado en
  `docker-compose.staging.yml`/INFRA-004, sin duplicar innecesariamente lo que
  ya funciona ahí).
- Nuevo `.env.production.example`, sin ningún valor por defecto para
  credenciales.
- Diferencias reales frente a staging: sin seed de datos de prueba
  (`scripts/seed-staging.sh` no debe correr acá), variables apuntando a un
  dominio/URL de producción (aunque sea simulado), y una nota explícita de qué
  cambiaría en un despliegue real (TLS, secretos gestionados, base de datos
  administrada — ya hay una tabla de esto en `docs/deployment.md`, sección
  "Diferencias esperadas frente a staging", que se puede usar como base).
- Actualizar `docs/deployment.md` para documentar el ambiente real ya creado,
  no solo la guía teórica que existe hoy.

**Módulos afectados:** Infraestructura, Documentación.
**Tecnologías:** Docker, Docker Compose.

**Pasos de implementación:**
1. Crear `docker-compose.production.yml` a partir de `docker-compose.staging.yml`.
2. Crear `.env.production.example` (sin defaults de credenciales, mismo criterio
   que `.env.staging.example`).
3. Confirmar que backend/frontend usan imágenes publicadas (`image:`, no
   `build:`) igual que en staging.
4. Remover/ajustar cualquier paso de seed de datos de prueba para este
   ambiente.
5. Levantar el ambiente localmente y verificar que arranca sano:
   `docker compose -f docker-compose.production.yml up -d`.
6. Verificar `/actuator/health` del backend y `/` del frontend.
7. Actualizar `docs/deployment.md` con la evidencia real de esta verificación
   (reemplazando la sección que hoy dice que no existe un ambiente de
   producción).
8. Actualizar `README.md` si hace falta (tabla de ambientes).

**Validaciones:** `docker-compose.production.yml` levanta sin errores; es un
ambiente claramente distinto de staging (no el mismo archivo con otro
nombre); sin datos de prueba/seed en este ambiente; documentado en
`docs/deployment.md` con evidencia real de la verificación.

**Casos de error:** si se reutiliza staging tal cual sin ninguna diferencia
real, no cuenta como un tercer ambiente distinto para la evaluación.

**Pruebas requeridas:** levantar el ambiente completo localmente y confirmar
que los servicios quedan healthy; confirmar `/actuator/health` del backend →
UP.

**Evidencias esperadas:** `docker-compose.production.yml` commiteado;
`docs/deployment.md` actualizado con capturas o logs de la verificación real.

**Criterios de aceptación:** existen los 3 ambientes que pide la consigna
(Development, Preview/Staging y Production), cada uno con su propio archivo de
compose y diferencias reales entre ellos.

**Definición de Done:** `docker-compose.production.yml` y
`.env.production.example` commiteados; `docs/deployment.md` ya no dice "no
existe un entorno de producción real"; PR aprobado.

---

## SEC-004 — Restringir acceso no autenticado al endpoint `/actuator/prometheus`

| Campo | Valor |
|---|---|
| Responsable | Persona B |
| Story Points | 3 |
| Prioridad | Alta |
| Etiquetas | seguridad, spring-security, actuator, hallazgo-testing-exploratorio |
| Dependencias | SEC-002 |

**Objetivo:** restringir el acceso al endpoint `/actuator/prometheus` para que
no exponga métricas de negocio sin autenticación.

**Descripción:** actualmente `/actuator/**` está en la lista `permitAll()` de
`SecurityConfig.java`, lo que permite a cualquiera (sin token) hacer `curl` a
`/actuator/prometheus` y ver el valor del inventario, productos críticos y
conteo de movimientos de stock. Hallazgo real de TEST-008
(`exploratory-charter-01-auth.md`, Bug #1).

**Contexto de negocio:** un endpoint de métricas sin protección filtra
información de negocio a cualquiera con acceso de red al puerto del backend.
Severidad **[MEDIUM]**.

**Alcance técnico:** separar `/actuator/prometheus` del resto de
`/actuator/**` que sí debe seguir público (`/actuator/health`, usado por los
`HEALTHCHECK` de Docker). Restringir por red interna (que solo Prometheus lo
alcance) o por scope/rol específico.

**Pasos de implementación:**
1. Sacar `/actuator/prometheus` del matcher `permitAll()` de `/actuator/**`.
2. Dejar `/actuator/health` en `permitAll()`.
3. Elegir mecanismo: scope nuevo/existente, o restricción de red vía
   `docker-compose.dev.yml`.
4. Verificar que Prometheus sigue scrapeando el endpoint tras el cambio.

**Validaciones:** `curl` sin token ya no devuelve 200; Prometheus sigue "up" en
targets; `/actuator/health` sigue público.

**Casos de error:** no bloquear por error `/actuator/health` (dejaría el
contenedor "unhealthy").

**Pruebas requeridas:** test en `SecurityIntegrationTest`/`AuthApiTest` que
confirme 401/403 sin token.

**Criterios de aceptación:** `/actuator/prometheus` ya no accesible sin
autenticación/red interna; resto de health checks sigue funcionando.

**Definición de Done:** cambio en `SecurityConfig.java` commiteado, test
automatizado, PR aprobado.

---

## SEC-005 — Activar protección contra fuerza bruta en el realm de Keycloak

| Campo | Valor |
|---|---|
| Responsable | Persona B |
| Story Points | 2 |
| Prioridad | Alta |
| Etiquetas | seguridad, keycloak, hallazgo-testing-exploratorio |
| Dependencias | SEC-001 |

**Objetivo:** activar la protección contra fuerza bruta en el realm de
Keycloak.

**Descripción:** se probaron 10 intentos de login fallidos seguidos contra
`admin@test.com` sin bloqueo ni delay — el intento #11 con la contraseña
correcta funcionó sin problema. `keycloak/realm.json` no tiene
`bruteForceProtected` configurado. Hallazgo real de TEST-008
(`exploratory-charter-01-auth.md`, Bug #2).

**Contexto de negocio:** sin esta protección, cualquiera puede intentar
adivinar contraseñas indefinidamente. Severidad **[MEDIUM]**.

**Alcance técnico:** agregar `"bruteForceProtected": true` y parámetros
asociados (`failureFactor`, `maxFailureWaitSeconds`, `waitIncrementSeconds`)
en `keycloak/realm.json`, usando valores por defecto razonables.

**Pasos de implementación:**
1. Editar `keycloak/realm.json` y activar `bruteForceProtected`.
2. Recrear el volumen de Keycloak (`docker compose -f docker-compose.dev.yml
   down -v && up -d`).
3. Repetir la prueba de 10 intentos fallidos y confirmar el bloqueo.

**Validaciones:** 10 intentos fallidos → bloqueo temporal; usuarios de prueba
siguen logueando normalmente bajo uso normal.

**Casos de error:** un umbral muy bajo podría bloquear los propios tests
automatizados — revisar contra la suite existente.

**Pruebas requeridas:** reproducir el bloqueo manualmente; correr la suite
completa (unit/integration/API/E2E) sin regresiones.

**Criterios de aceptación:** intentos fallidos consecutivos contra el mismo
usuario resultan en bloqueo/demora real.

**Definición de Done:** `realm.json` actualizado, verificado contra el stack
real, PR aprobado.

---

## SEC-006 — Revocar refresh tokens ya rotados en Keycloak

| Campo | Valor |
|---|---|
| Responsable | Persona A |
| Story Points | 3 |
| Prioridad | Alta |
| Etiquetas | seguridad, keycloak, oauth2, hallazgo-testing-exploratorio |
| Dependencias | SEC-001, SEC-003 |

**Objetivo:** revocar automáticamente un refresh token cuando ya fue rotado
(usado una vez para pedir un token nuevo).

**Descripción:** se hizo el flujo normal de refresh (token A → token B) y
luego se reusó el token A original — Keycloak lo aceptó y devolvió un par de
tokens nuevo, en vez de rechazarlo. `keycloak/realm.json` no tiene
`revokeRefreshToken` configurado. Hallazgo real de TEST-008
(`exploratory-charter-01-auth.md`, Bug #3).

**Contexto de negocio:** si un refresh token es interceptado/filtrado, hoy
sigue siendo válido incluso después de rotado, reduciendo la ventana de
detección. Severidad **[MEDIUM]**.

**Alcance técnico:** agregar `"revokeRefreshToken": true` en
`keycloak/realm.json` y revisar `refreshTokenMaxReuse` (0 por defecto
esperado).

**Pasos de implementación:**
1. Editar `keycloak/realm.json` y activar `revokeRefreshToken`.
2. Recrear el volumen de Keycloak.
3. Repetir el flujo (token A → token B → reusar token A) y confirmar rechazo.

**Validaciones:** refresh token ya rotado es rechazado; flujo normal de
refresh de `AuthContext.jsx` (`keycloak.updateToken(30)`) sigue funcionando.

**Casos de error:** si el frontend refresca en paralelo (condición de
carrera), podría fallar una de las dos solicitudes — probar el flujo real de
la SPA, no solo con `curl`.

**Pruebas requeridas:** reproducir el escenario manualmente; correr
`auth.spec.js` (E2E) sin regresiones.

**Criterios de aceptación:** un refresh token usado una vez deja de ser
válido.

**Definición de Done:** `realm.json` actualizado, verificado con el flujo real
de la SPA, PR aprobado.

---

## BACK-009 — Validaciones de rango en `price` y `performedBy`

| Campo | Valor |
|---|---|
| Responsable | Persona A |
| Story Points | 3 |
| Prioridad | Media |
| Etiquetas | backend, validacion, bean-validation, hallazgo-testing-exploratorio |
| Dependencias | BACK-002, BACK-005 |

**Objetivo:** agregar las validaciones de longitud/rango que faltan en
`price` y `performedBy` para que los errores de base de datos se reporten con
un mensaje correcto, no como un falso "SKU duplicado".

**Descripción:** un precio como `99999999999.99` (no cabe en `NUMERIC(10,2)`)
hace que el backend responda "Conflicto de integridad de datos: el recurso ya
existe o está en uso" — el mismo mensaje que un SKU duplicado — cuando el
problema real es un overflow numérico (`SQLState 22003`). Mismo problema en
`performedBy`, sin `@Size`, que puede exceder `VARCHAR(255)`. Hallazgo más
profundizado de las 3 sesiones de TEST-008 (`exploratory-charter-02-forms.md`,
Bug #4).

**Contexto de negocio:** el caso de `price` es alcanzable por un simple error
de tipeo, y el mensaje confunde al usuario. Severidad **[MEDIUM]**.

**Alcance técnico:**
- `ProductRequestDTO.java`: agregar `@Digits(integer = 8, fraction = 2)` a
  `price`.
- `StockMovementRequestDTO.java` y `StockAdjustmentRequestDTO.java`: agregar
  `@Size(max = 255)` a `performedBy`.
- Revisar `GlobalExceptionHandler.java`: hoy toda
  `DataIntegrityViolationException` se reporta como duplicado sin mirar la
  causa real; con `@Valid` estos casos deben atraparse antes de llegar a la
  BD (400, no 409).

**Pasos de implementación:**
1. Agregar `@Digits` a `ProductRequestDTO.price`.
2. Agregar `@Size(max = 255)` a `performedBy` en ambos DTOs de stock.
3. Repetir el escenario original (`price = 99999999999.99`) y confirmar 400
   con mensaje claro.
4. Repetir con `performedBy` > 255 caracteres vía API directa.

**Validaciones:** `price` fuera de rango → 400 con mensaje real (no
"duplicado"); `performedBy` largo → 400 antes de llegar a la BD.

**Casos de error:** verificar que no rompan tests con valores límite
existentes.

**Pruebas requeridas:** nuevos casos en `ProductRequestDTOValidationTest` y
tests de stock; suite completa de backend sin regresiones.

**Criterios de aceptación:** `price` y `performedBy` fuera de rango devuelven
400 con mensaje que refleja el problema real, nunca "recurso duplicado".

**Definición de Done:** anotaciones agregadas, tests nuevos, PR aprobado.

---

## FRONT-006 — Advertir stock disponible al desactivar un producto

| Campo | Valor |
|---|---|
| Responsable | Persona B |
| Story Points | 1 |
| Prioridad | Baja |
| Etiquetas | frontend, ux, hallazgo-testing-exploratorio |
| Dependencias | FRONT-003 |

**Objetivo:** avisar al usuario cuando desactiva un producto que todavía
tiene stock disponible.

**Descripción:** al desactivar un producto con 500 unidades en stock, el
diálogo de confirmación solo dice "¿Eliminar el producto X?", sin mencionar el
stock. Hallazgo de TEST-008 (`exploratory-charter-03-stock.md`, Bug #5) —
mejora de UX, no defecto funcional (el soft-delete funciona correctamente,
ADR-001).

**Contexto de negocio:** un usuario podría desactivar sin querer un producto
con inventario real. Severidad **[LOW]**.

**Alcance técnico:** `ProductsTable.jsx` — el `handleDelete` usa
`window.confirm`; incluir la cantidad en stock en el mensaje cuando sea mayor
a 0.

**Pasos de implementación:**
1. Pasar `quantity` a `handleDelete`.
2. Si `quantity > 0`: `¿Eliminar el producto "${name}"? Todavía tiene
   ${quantity} unidades en stock.`
3. Si `quantity === 0`: sin cambios.

**Validaciones:** diálogo muestra la cantidad cuando stock > 0; sin cambio de
comportamiento cuando stock = 0.

**Pruebas requeridas:** test en `products.spec.js` (E2E) o test unitario de
`ProductsTable`.

**Criterios de aceptación:** el diálogo menciona el stock actual cuando es
mayor a 0.

**Definición de Done:** cambio en `ProductsTable.jsx`, test que lo cubre, PR
aprobado.
