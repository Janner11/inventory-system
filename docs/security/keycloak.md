# Keycloak — Configuración de Seguridad

Configuración completa del proveedor de identidad (Keycloak 24) usado por
este proyecto: realm, clients, modelo de scopes/roles, usuarios de prueba y
cómo el backend valida cada JWT. Ver también
[`docs/architecture.md`](../architecture.md#flujo-de-autenticación-oauth2-pkce)
para el diagrama de secuencia del flujo completo.

## Índice

1. [Realm](#realm)
2. [Clients](#clients)
3. [Modelo de autorización granular (scopes)](#modelo-de-autorización-granular-scopes)
4. [Roles de realm (composite)](#roles-de-realm-composite)
5. [Usuarios de prueba](#usuarios-de-prueba)
6. [Cómo el backend valida un JWT](#cómo-el-backend-valida-un-jwt)
7. [Reproducibilidad — `keycloak/realm.json`](#reproducibilidad--keycloakrealmjson)
8. [Operación — comandos útiles](#operación--comandos-útiles)

## Realm

| Parámetro | Valor |
|---|---|
| Nombre | `inventario` |
| Issuer (`iss`) | `http://localhost:8080/realms/inventario` |
| JWKS URI (interno, red Docker) | `http://keycloak:8080/realms/inventario/protocol/openid-connect/certs` |
| Hostname | `KC_HOSTNAME=localhost` fijo, `KC_HOSTNAME_STRICT=true` |

`KC_HOSTNAME` está fijado a `localhost` (no auto-detectado) para que el claim
`iss` emitido en cada token sea **siempre el mismo**, sin importar si la
petición al endpoint de token viene del navegador (host) o de otro
contenedor de la red Docker (backend). El backend separa esta responsabilidad
en dos propiedades (`keycloak.issuer-uri` público vs. `keycloak.jwk-set-uri`
interno, ver [`SecurityConfig`](#cómo-el-backend-valida-un-jwt)) precisamente
por esta asimetría host↔contenedor.

## Clients

| Client ID | Tipo | PKCE | Redirect URIs | Uso |
|---|---|---|---|---|
| `inventario-frontend` | Público | Sí (S256) | `http://localhost:5173/*` (dev), `http://localhost:8090/*` (staging) | SPA — Authorization Code + PKCE |
| `inventario-backend` | Confidencial | — | — | Resource Server (valida JWT); `directAccessGrantsEnabled=true` habilita el *password grant* para pruebas con `curl`/tests de integración, **solo en este entorno de desarrollo** |

El secret de `inventario-backend` en desarrollo es `inventario-backend-secret`
(`keycloak/realm.json`, sección `clients`) — **nunca** se reutiliza este
valor fuera de `docker-compose.dev.yml`; en staging/producción se genera uno
real y se inyecta vía `KEYCLOAK_CLIENT_SECRET` (ver
[`docs/deployment.md`](../deployment.md)).

## Modelo de autorización granular (scopes)

Cada scope es un **client role** de `inventario-backend`. La regla del
proyecto es siempre validar el scope individual del endpoint, nunca un rol
genérico:

```java
// ✅ correcto
@PreAuthorize("hasAuthority('SCOPE_product:view')")

// ❌ prohibido — no se valida por nombre de rol
@PreAuthorize("hasRole('ADMIN')")
```

| Scope | Módulo | Endpoints que lo exigen |
|---|---|---|
| `product:view` | Productos | `GET /api/products*` |
| `product:manage` | Productos | `POST`/`PUT`/`DELETE /api/products*` |
| `stock:view` | Stock | `GET /api/stock/movements*`, `GET /api/stock/alerts` |
| `stock:manage` | Stock | `POST /api/stock/entry`\|`exit`\|`adjust` |
| `report:view` | Dashboard/Reportes | `GET /api/dashboard/*`, `GET /api/reports/*` |
| `audit:view` | Auditoría | `GET /api/audit/products/{id}/revisions` |
| `user:manage` | Seguridad | Reservado — sin endpoint de gestión de usuarios en el backend todavía |
| `actuator:view` | Observabilidad | `GET /actuator/prometheus` (SEC-004) — no es un endpoint de negocio, protege el scrape de métricas. Asignado a `ADMIN` y al service account de `inventario-backend` (Prometheus se autentica con `client_credentials`, no un usuario humano) |

## Roles de realm (composite)

Los 5 roles son **realm roles composite** — cada uno agrega los client roles
de `inventario-backend` correspondientes (`keycloak/realm.json`,
`roles.realm[].composites.client`). Un usuario recibe **un solo** realm role;
Keycloak expande los composites al emitir el JWT, así que
`resource_access.inventario-backend.roles` siempre contiene los scopes
resueltos sin necesitar asignar client roles directamente a cada usuario.

| Rol | Scopes que agrega |
|---|---|
| `ADMIN` | los 8 scopes |
| `MANAGER` | `product:view`, `product:manage`, `stock:view`, `stock:manage`, `report:view` |
| `WAREHOUSE` | `product:view`, `stock:view`, `stock:manage` |
| `VIEWER` | `product:view`, `stock:view`, `report:view` |
| `AUDITOR` | `audit:view`, `report:view` |

## Usuarios de prueba

Solo existen en el realm de **desarrollo/staging** (`keycloak/realm.json`) —
nunca se crean en un despliegue de producción real.

| Usuario | Password | Rol |
|---|---|---|
| `admin@test.com` | `admin123` | `ADMIN` |
| `manager@test.com` | `manager123` | `MANAGER` |
| `warehouse@test.com` | `warehouse123` | `WAREHOUSE` |
| `viewer@test.com` | `viewer123` | `VIEWER` |
| `auditor@test.com` | `auditor123` | `AUDITOR` |

## Cómo el backend valida un JWT

`backend/src/main/java/com/inventario/`:

- **`config/SecurityConfig.java`** — bean `JwtDecoder` (`NimbusJwtDecoder.withJwkSetUri(...)`,
  usando el JWKS interno) + `SecurityFilterChain` con `@PreAuthorize` por
  endpoint (`@EnableMethodSecurity`) + `CorsConfigurationSource` (orígenes
  desde `CORS_ALLOWED_ORIGINS`).
- **`security/JwtAuthConverter.java`** — combina las authorities estándar del
  claim `scope` con las de `KeycloakGrantedAuthoritiesConverter`.
- **`security/KeycloakGrantedAuthoritiesConverter.java`** — lee
  `resource_access.inventario-backend.roles` del JWT y convierte cada scope
  en un `GrantedAuthority` con prefijo `SCOPE_` (de ahí
  `hasAuthority('SCOPE_product:view')` en los controllers).
- **`security/CurrentUserResolver.java`** — resuelve el usuario actual
  (claim `preferred_username`, con `sub` como fallback) para
  `@CreatedBy`/`AuditRevisionListener` (Hibernate Envers, ver
  [`docs/architecture.md`](../architecture.md#arquitectura-del-backend)).

## Reproducibilidad — `keycloak/realm.json`

Todo el realm (clients, scopes, roles, usuarios) se exporta como
`keycloak/realm.json` y se versiona en Git (ADR-008). Keycloak lo importa
automáticamente al arrancar (`--import-realm`) — cualquier desarrollador
obtiene un ambiente de seguridad idéntico sin configuración manual.

> La importación con `IGNORE_EXISTING` solo aplica una vez por volumen. Si se
> modifica `keycloak/realm.json` y se quiere reimportar sobre un volumen ya
> existente, hay que recrearlo:
> `docker compose -f docker-compose.dev.yml down -v && docker compose -f docker-compose.dev.yml up -d`.

## Operación — comandos útiles

Obtener un JWT (password grant, solo dev/staging):

```bash
curl -s -X POST "http://localhost:8080/realms/inventario/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=inventario-backend" \
  -d "client_secret=inventario-backend-secret" \
  -d "username=viewer@test.com" \
  -d "password=viewer123"
```

Probar un endpoint protegido:

```bash
# Sin token → 401
curl -i http://localhost:8081/api/products

# Con token → 200 (viewer@test.com tiene product:view)
curl -i -H "Authorization: Bearer <ACCESS_TOKEN>" http://localhost:8081/api/products
```

Decodificar los scopes de un token (sin verificar firma, solo para
inspección local):

```bash
echo "<ACCESS_TOKEN>" | cut -d. -f2 | base64 -d 2>/dev/null | python3 -m json.tool
```
