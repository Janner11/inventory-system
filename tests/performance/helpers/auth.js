import http from 'k6/http';
import { check } from 'k6';

/**
 * Login/refresh de token de Keycloak para tests de carga con k6 (TEST-006).
 *
 * El realm `inventario` tiene un access token de vida corta (300s / 5 min, verificado
 * contra el stack real) y un refresh token de 1800s (30 min) — un load test de 5 min ya
 * roza el límite del access token, y stress-test/soak-test lo superan ampliamente. En vez
 * de pedir un token nuevo por request (agregaría latencia de Keycloak a las métricas que
 * se quieren medir del backend), cada VU mantiene su propio token en el scope del módulo
 * (k6 evalúa el módulo una vez por VU, así que estas variables no se comparten entre VUs)
 * y lo refresca solo cuando está por expirar.
 */

const KEYCLOAK_URL = __ENV.KEYCLOAK_URL || 'http://localhost:8080';
const KEYCLOAK_REALM = __ENV.KEYCLOAK_REALM || 'inventario';
const CLIENT_ID = __ENV.KEYCLOAK_CLIENT_ID || 'inventario-backend';
const CLIENT_SECRET = __ENV.KEYCLOAK_CLIENT_SECRET || 'inventario-backend-secret';
const USERNAME = __ENV.PERF_USERNAME || 'admin@test.com';
const PASSWORD = __ENV.PERF_PASSWORD || 'admin123';

const TOKEN_URL = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;

// Margen de seguridad antes de la expiración real para evitar que un request en vuelo
// reciba un 401 justo cuando el token vence.
const EXPIRY_BUFFER_SECONDS = 30;

let tokenState = null; // { accessToken, refreshToken, expiresAt } — por VU (ver comentario arriba)

function login() {
  const res = http.post(
    TOKEN_URL,
    {
      grant_type: 'password',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      username: USERNAME,
      password: PASSWORD,
    },
    { tags: { name: 'KeycloakLogin' } },
  );

  check(res, { 'login: 200': (r) => r.status === 200 });

  const body = res.json();
  tokenState = {
    accessToken: body.access_token,
    refreshToken: body.refresh_token,
    expiresAt: Date.now() + body.expires_in * 1000,
  };
}

function refresh() {
  const res = http.post(
    TOKEN_URL,
    {
      grant_type: 'refresh_token',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      refresh_token: tokenState.refreshToken,
    },
    { tags: { name: 'KeycloakRefresh' } },
  );

  if (res.status !== 200) {
    // El refresh token también puede haber expirado (soak test largo, VU inactivo, etc.) —
    // en ese caso simplemente se vuelve a loguear desde cero.
    login();
    return;
  }

  const body = res.json();
  tokenState = {
    accessToken: body.access_token,
    refreshToken: body.refresh_token,
    expiresAt: Date.now() + body.expires_in * 1000,
  };
}

/** Devuelve un access token válido para el VU actual, logueando/refrescando si hace falta. */
export function getValidToken() {
  if (!tokenState) {
    login();
  } else if (Date.now() > tokenState.expiresAt - EXPIRY_BUFFER_SECONDS * 1000) {
    refresh();
  }
  return tokenState.accessToken;
}

export function authHeaders() {
  return {
    Authorization: `Bearer ${getValidToken()}`,
    'Content-Type': 'application/json',
  };
}
