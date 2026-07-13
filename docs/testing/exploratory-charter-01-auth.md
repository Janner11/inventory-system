# TEST-008 – Sesión 1: Autenticación y autorización

## ¿Qué me propuse hacer?

La idea era meterle mano a la autenticación y autorización del proyecto, tanto desde el
backend como desde el frontend real, usando `curl`, Playwright y las DevTools del
navegador. Quería ver si podía colarme en endpoints que no me correspondían, jugar con
tokens JWT a ver si se rompía algo, o encontrar datos que se estuvieran escapando sin
pedir login.

## ¿Contra qué estaba probando?

La aplicación real levantada con `docker-compose.dev.yml`:
- Backend en Spring, Postgres, Keycloak 24, realm `inventario`.
- Cinco usuarios de prueba con sus roles (admin, manager, warehouse, viewer y auditor,
  todos con @test.com), justo para probar la matriz de permisos.
- El frontend corriendo en local (`npm run dev`, puerto 5173) para inspeccionar
  almacenamiento y cookies después del login.

**Áreas clave:** `SecurityConfig`/`JwtAuthConverter`, flujo OAuth2 PKCE con Keycloak,
endpoints `/api/**` con scopes, y `/actuator/**`.

**Tiempo que le dediqué:** 49 minutos netos (de 19:52 a 20:41), sin contar preparación del
entorno ni escribir esto después.

## Lo que fui probando (paso a paso)

1.  **Lo básico con tokens:** Me bajé tokens reales de los 5 usuarios con password grant
    y empecé a jugar con el header `Authorization`. Sin token (401, bien), con basura
    (401, bien), sin `Bearer` (401, bien). Probé con `bearer` en minúscula y me dio 200:
    pensé que quizás era un bug, pero revisé la RFC 7235 y resulta que el auth-scheme es
    case-insensitive, así que nada, correcto.

2.  **Ataques clásicos de JWT:** Intenté los tres de manual: `alg:none` sin firma,
    `alg:RS256` con firma vacía, y la confusión RS256→HS256 (intentando firmar con un
    secreto inventado). Los tres me devolvieron 401 sin rechistar. Spring Security está
    validando la firma contra las JWKS de Keycloak como debe ser, no se deja engañar. Bien.

3.  **Crucé la matriz de permisos:** Agarré cada rol y lo lancé contra endpoints que no le
    tocaban. Viewer intentando crear producto (403), auditor listando productos (403),
    warehouse viendo auditoría (403), viewer viendo dashboard (200, porque sí tiene
    `report:view`). Todo se comportó exactamente como dice la matriz de permisos de la doc.
    Impecable.

4.  **Actuator:** Revisé qué endpoints quedaban públicos sin login. `health`, `info` y
    `prometheus` respondían 200 sin token; el resto (`env`, `beans`, `heapdump`) bien
    devolvían 401. El problema es que `prometheus` está soltando métricas de negocio a
    cualquiera que llegue al puerto. Lo detallo más abajo en el Hallazgo #1.

5.  **Optimistic locking:** Quise ver si podía saltarme el `@Version` de Hibernate
    inyectando un `"version": 99999` en un `PUT /api/products/{id}`. No hubo caso.
    Hibernate ignoró mi versión falsa e incrementó la real de 0 a 1. El DTO no expone ese
    campo, así que todo seguro. *(Ojo: para esta prueba machaqué temporalmente el producto
    semilla ALI-COFFEE-008, pero lo restauré al final, ver nota al pie.)*

6.  **Fuerza bruta en login:** Hice 10 intentos de login fallidos seguidos y rápidos contra
    Keycloak con contraseña incorrecta. Todos devolvieron 401 limpio. Sin bloqueo, sin
    delay incremental, nada. Ver Hallazgo #2.

7.  **Refresh token reusado:** Hice el flujo normal de refresh (obtengo token A, roto a
    token B, e intento usar A otra vez). El token A, que ya debería estar muerto, siguió
    funcionando y me dio un par nuevo. Keycloak no está revocando los refresh tokens viejos
    al rotar. Ver Hallazgo #3.

8.  **CORS:** Probé un preflight con `Origin: http://evil-attacker.com` (no está en la
    lista blanca). Bien rechazado con 403 "Invalid CORS request", sin header
    `Access-Control-Allow-Origin`. Correcto.

9.  **Almacenamiento en frontend:** Levanté el frontend real, hice login con Playwright
    (flujo PKCE completo) y revisé `localStorage` y `sessionStorage`. Estaban vacíos, el
    JWT solo vive en memoria dentro de `keycloak-js`. Las cookies eran las de sesión de
    Keycloak (`KEYCLOAK_IDENTITY`, `AUTH_SESSION_ID`), marcadas `httpOnly`. Tal como debe
    ser.

## Lo que encontré (lo malo)

### [MEDIUM] `/actuator/prometheus` suelta métricas de negocio sin autenticación

Esto fue un pequeño shock. Haciendo un simple `curl` a `http://localhost:8081/actuator/prometheus`
sin token me escupió cosas como:

inventory_value{application="inventario-backend"} 47001.01
products{application="inventario-backend",status="ACTIVE"} 102.0
products_critical{application="inventario-backend"} 15.0
stock_movements{application="inventario-backend",type="ENTRY"} 32669.0

Cualquiera con acceso al puerto del backend sabe el valor exacto del inventario, cuántos
productos están en estado crítico, y el volumen de movimientos, sin necesidad de loguearse.
En local no es grave, pero en producción esto debería estar detrás de un firewall o al
menos no publicado al host. Recomiendo restringir el acceso en `docker-compose.staging.yml`
o en `SecurityConfig` en vez de un simple `permitAll()`.

### [MEDIUM] Sin protección de fuerza bruta en el login de Keycloak

Diez intentos fallidos seguidos y la cuenta `admin@test.com` seguía aceptando el intento #11
con la contraseña buena como si nada. Revisé el `realm.json` y no encontré nada de
`bruteForceProtected`. Keycloak tiene esa funcionalidad nativa, pero no la hemos activado.
Facilita diccionarios y credential stuffing si llega a estar expuesto. Habilitaría
`bruteForceProtected: true` con un `failureFactor` y `waitIncrementSeconds` razonables.

### [MEDIUM] Reuso de refresh token ya rotado

El flujo de refresh de Keycloak no está revocando el token viejo después de usarlo una vez.
Haces refresh, te da un token B, y el token A sigue siendo válido hasta que expire (30 min).
Busqué `revokeRefreshToken` en el realm y no aparece. Es una capa extra de seguridad: si
alguien intercepta un refresh token de un log, podría seguir renovando sesiones aunque el
usuario legítimo ya haya rotado. Como el proyecto no persiste tokens en `localStorage` el
riesgo es menor, pero igual recomendaría activar `"revokeRefreshToken": true` y
`"refreshTokenMaxReuse": 0` por si acaso.

## Lo que encontré (lo bueno)

Cosas que están bien blindadas y vale la pena anotar porque no todo es buscar fallos:

- Los tres ataques de manipulación de JWT (`alg:none`, firma vacía, confusión HS256) son
  rechazados correctamente. Spring Security no se inmuta.
- La matriz de autorización por scope se cumple a rajatabla para los cinco roles probados
  contra endpoints que no les tocan.
- El optimistic locking no se puede burlar metiendo una versión falsa en el JSON.
- CORS rechaza sin piedad orígenes no permitidos.
- El JWT jamás toca `localStorage`/`sessionStorage` del navegador, solo vive en memoria.
- Los endpoints sensibles de Actuator (`env`, `beans`, `heapdump`) sí piden autenticación,
  a diferencia de `prometheus`.

## Qué dejaría para la próxima sesión

- Probar el `check-sso` silencioso cuando el token de Keycloak expira a mitad de una sesión
  larga (¿se reautentica solo o el usuario ve un error feo?).
- Verificar si está habilitado el registro de usuarios auto-gestionado
  (self-registration) en Keycloak. Sería un hallazgo importante si es el caso.
- Jugar con la manipulación del `redirect_uri` en el flujo de autorización para ver si
  hay open redirect.

## ¿Qué tanto cubrí?

Diría que un ~60% del área de autenticación y autorización. Quedó bien cubierto: validación
de firma JWT, matriz de scopes, actuator, CORS, almacenamiento de tokens. Me faltó probar
flujos de recuperación de contraseña (no implementados aún), rotación de llaves JWKS y el
escenario de sesión larga que mencioné antes.

## Nota final (para que no quede el producto roto)

El producto `ALI-COFFEE-008` (Café Molido Premium, TEST-007) lo modifiqué al hacer la
prueba de optimistic locking. Le había puesto `name: "Test Version Bypass"`, `sku:
"VERSION-TEST"` y `quantity: 1`. Lo dejé exactamente como estaba al inicio (`name`, `sku`,
`description`, `quantity: 40`) usando dos `PUT` adicionales. Verifiqué con un `GET` que
todo coincidiera con `V8__insert_seed_data.sql`, así que sin daños colaterales.