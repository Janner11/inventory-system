# Reporte de Testing Exploratorio – TEST-008

Este es el resumen de las tres sesiones de testing exploratorio que hice el 11 de julio de
2026, usando `curl`, scripts en Python y Playwright contra el stack real del proyecto
(`docker-compose.dev.yml`, con Postgres, Keycloak 24 y el backend). Básicamente, me puse a
jugar a romper cosas para ver qué pasaba.

Los charters completos, con el paso a paso de cada sesión, están en estos archivos:

- [`exploratory-charter-01-auth.md`](exploratory-charter-01-auth.md) – Autenticación y autorización
- [`exploratory-charter-02-forms.md`](exploratory-charter-02-forms.md) – Formularios y validaciones
- [`exploratory-charter-03-stock.md`](exploratory-charter-03-stock.md) – Movimientos de stock

> **Sobre GitHub Issues:** el plan original pedía que cada bug fuera un Issue con label
> `bug`, pero como el repo es público, decidí no crearlos sin confirmar antes. Así que los
> 5 bugs están documentados aquí mismo, con todos los datos que llevaría un Issue real:
> título, pasos, severidad y evidencia. Si más adelante se decide abrirlos, es copiar y
> pegar.

> **Actualización:** los 5 bugs de acá abajo (más un gap de infraestructura detectado por
> separado, falta de un ambiente de Production real) ya se formalizaron como 6 tickets de
> backlog listos para trabajarse, con alcance técnico/pasos/DoD completos — ver
> [`hallazgos-test-008-tickets.md`](hallazgos-test-008-tickets.md).

## Resumen rápido

| Charter | Cuánto duró | Bugs encontrados | Cosas que SÍ funcionaron bien |
|---------|-------------|-------------------|-------------------------------|
| 1 — Auth | 49 min | 3 (Medios) | 6 |
| 2 — Formularios | 61 min | 1 (Medio, 3 puntos) | 4 |
| 3 — Stock | 37 min | 1 (Bajo) | 4 |
| **Total** | **~147 min (2.5h)** | **5** | **14** |

Para mí, el testing no es solo encontrar fallos; confirmar que algo importante aguanta
como debe también es un hallazgo valioso. Por eso cada sesión incluye los "hallazgos
positivos", no solo los bugs.

## Bugs encontrados (la lista completa)

### [MEDIUM] 1. `/actuator/prometheus` suelta métricas de negocio sin pedir autenticación

- **Charter:** 1 (Auth)
- **Cómo lo vi:** un simple `curl http://localhost:8081/actuator/prometheus` sin token me
  devolvió, entre otras cosas, el valor exacto del inventario, cuántos productos hay en
  estado crítico y el conteo de movimientos de stock. Cualquiera con acceso al puerto
  puede verlo.
- **Recomendación:** en un entorno real, que este endpoint solo esté disponible para el
  colector de Prometheus (red interna) o que pida autenticación.
- **Estado:** Abierto — formalizado como [SEC-004](hallazgos-test-008-tickets.md#sec-004--restringir-acceso-no-autenticado-al-endpoint-actuatorprometheus).

### [MEDIUM] 2. No hay protección contra fuerza bruta en el login de Keycloak

- **Charter:** 1 (Auth)
- **Cómo lo vi:** le metí 10 intentos fallidos seguidos al usuario `admin@test.com`. Todos
  devolvieron `401`, sin bloqueo ni delay. La cuenta aceptó el intento #11 con la
  contraseña buena sin chistar.
- **Recomendación:** activar `"bruteForceProtected": true` en `keycloak/realm.json`.
- **Estado:** Abierto — formalizado como [SEC-005](hallazgos-test-008-tickets.md#sec-005--activar-protección-contra-fuerza-bruta-en-el-realm-de-keycloak).

### [MEDIUM] 3. Un refresh token que ya rotó sigue siendo válido

- **Charter:** 1 (Auth)
- **Cómo lo vi:** hice el flujo normal de refresh (token A → token B), y luego volví a
  usar el token A original. Me dio un 200 y un par de tokens nuevos, en lugar de
  rechazarlo.
- **Recomendación:** agregar `"revokeRefreshToken": true` al realm.
- **Estado:** Abierto — formalizado como [SEC-006](hallazgos-test-008-tickets.md#sec-006--revocar-refresh-tokens-ya-rotados-en-keycloak).

### [MEDIUM] 4. Mensaje de error súper confuso cuando un valor se pasa del límite (precio y `performedBy`)

- **Charter:** 2 (Formularios) — Este fue el hallazgo más sólido de las tres sesiones. Lo
  perseguí hasta el fondo.
- **Cómo lo vi:** puse un precio de `99999999999.99` en el formulario de nuevo producto
  (el campo solo valida que sea > 0 en el frontend). Cuando guardé, me saltó un mensaje
  que decía *"Conflicto de integridad de datos: el recurso ya existe o esta en uso"*. Un
  mensaje de duplicado, cuando el problema real era que el número no cabía en la columna
  `NUMERIC(10,2)`. Los logs confirmaban `SQLState 22003 — numeric field overflow`.
- **Causa:** `ProductRequestDTO.price` no tiene `@Digits(integer=8, fraction=2)`, y lo
  mismo pasa con `performedBy` en los DTOs de stock (no tienen `@Size(max=255)`). Además,
  el `GlobalExceptionHandler` agarra cualquier `DataIntegrityViolationException` y siempre
  suelta ese texto de duplicado, sin mirar la causa real.
- **Alcance:** el fallo con el precio se lo puede encontrar un usuario normal (un typo
  tonto). El de `performedBy` solo si alguien llama directo a la API.
- **Recomendación:** poner las anotaciones que faltan en los tres campos.
- **Estado:** Abierto — formalizado como [BACK-009](hallazgos-test-008-tickets.md#back-009--validaciones-de-rango-en-price-y-performedby).

### [LOW] 5. Desactivar un producto con stock no avisa de la cantidad que queda

- **Charter:** 3 (Stock)
- **Cómo lo vi:** desactivé un producto con 500 unidades en stock. El diálogo de
  confirmación solo decía "¿Eliminar el producto X?", sin mencionar la cantidad.
- **Recomendación:** si se quiere mejorar, incluir el stock en el mensaje cuando sea > 0.
- **Estado:** Abierto (es más una sugerencia de UX que un defecto funcional) — formalizado
  como [FRONT-006](hallazgos-test-008-tickets.md#front-006--advertir-stock-disponible-al-desactivar-un-producto).

## Lo más bestia que confirmé como seguro: cero sobreventa con concurrencia real

El experimento estrella del Charter 3 fue lanzar 5 peticiones de salida de stock al mismo
tiempo (concurrencia real, no de una en una) pidiendo en total más unidades de las que
había. El optimistic locking de JPA (`@Version`) funcionó de maravilla: 3 de los 5
requests se completaron correctamente (dejando el stock en 1), y los otros 2 rebotaron con
un `409` y un mensaje claro. El stock jamás se fue a negativo. Para un sistema de
inventario, esta es una de las garantías más importantes, y ver que aguanta en un
escenario vivo (no solo en un test unitario) fue el mejor hallazgo de todas las sesiones.

## Otras cosas que salieron bien (por si acaso)

**Del Charter 1 (Auth):** los ataques clásicos de manipulación de JWT (`alg:none`, firma
vacía, confusión RS256→HS256) fueron rechazados sin piedad; la matriz de permisos por
scope se cumplió a rajatabla para los cinco roles; el optimistic locking no se deja
manipular desde el cliente; CORS manda a paseo orígenes no permitidos; y el JWT nunca se
guarda en `localStorage`/`sessionStorage`.

**Del Charter 2 (Formularios):** el frontend escapa todo el HTML de forma consistente y no
usa `dangerouslySetInnerHTML` por ningún lado; la inyección SQL se trata como texto
literal (gracias, JPA); `@NotBlank` pilla los espacios en blanco; y los campos que tienen
`@Size` dan errores claritos.

**Del Charter 3 (Stock):** cantidades negativas, cero, producto inexistente, producto
inactivo… todo devuelve el código HTTP correcto y un mensaje útil. Incluso el caso justo
de vaciar el stock a cero se maneja sin que un `>` en lugar de `>=` lo rompa.

## Ideas para la próxima (lo que me dejé en el tintero)

- Del Charter 1: probar el `check-sso` silencioso cuando el token expira a media sesión;
  ver si está activo el autoregitro de usuarios en Keycloak; y jugar con el
  `redirect_uri` para ver si hay open redirect.
- Del Charter 2: auditar los formularios de ajuste de stock por el mismo problema de
  "falta de tope"; probar caracteres de control y null bytes; y ver qué tal se comporta
  todo sin JavaScript en el navegador.
- Del Charter 3: concurrencia mezclando entradas, salidas y ajustes al mismo tiempo;
  simular que dos personas editan el mismo producto a la vez desde el frontend; y ver
  cuántos reintentos automáticos hace el frontend ante un 409.

## Cómo trabajé

Usé el formato de charter del Session-Based Test Management (SBTM, de James y Jonathan
Bach) — la plantilla está en [`exploratory-charter-template.md`](exploratory-charter-template.md).
Cada sesión tenía un tiempo limitado (no buscaba probar absolutamente todo) y fui a fondo
con los hallazgos más prometedores. Por ejemplo, el Bug #4 lo confirmé revisando el
código, los logs y reproduciéndolo con Playwright desde la UI real, no solo con `curl`.

Y para no dejar basura, todos los productos de prueba que creé en las tres sesiones los
desactivé con soft-delete al terminar cada charter. El entorno local quedó limpio.

## Un gap más, fuera de los 5 bugs

Al formalizar el seguimiento de estos hallazgos también se detectó que el proyecto solo
tiene 2 de los 3 ambientes que exige la consigna (Development y Preview/Staging, sin
Production real) — no es un bug de comportamiento como los 5 de arriba, sino un gap de
infraestructura. Quedó igual de formalizado como ticket:
[INFRA-005](hallazgos-test-008-tickets.md#infra-005--crear-ambiente-de-production-real-y-separado-3er-ambiente-obligatorio).