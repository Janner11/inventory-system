import { expect } from '@playwright/test';

/**
 * Credenciales de los usuarios de prueba de Keycloak (realm `inventario`,
 * ver keycloak/realm.json y CLAUDE.md sección 6). Se usan directamente en
 * vez de mockear el login porque estos specs ejercitan el flujo OAuth2 PKCE
 * real contra un Keycloak real (mismo criterio que auth.spec.js/products.spec.js
 * del avance de TEST-004).
 */
export const USERS = {
  admin: { username: 'admin@test.com', password: 'admin123' },
  manager: { username: 'manager@test.com', password: 'manager123' },
  warehouse: { username: 'warehouse@test.com', password: 'warehouse123' },
  viewer: { username: 'viewer@test.com', password: 'viewer123' },
  auditor: { username: 'auditor@test.com', password: 'auditor123' },
};

/** Ejecuta el flujo de login OAuth2 PKCE completo contra Keycloak y espera a llegar a /dashboard. */
export async function login(page, { username, password }) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.waitForURL(/realms\/inventario/);
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('#kc-login');
  await page.waitForURL('**/dashboard');
}

export async function loginAsAdmin(page) {
  await login(page, USERS.admin);
}

export async function loginAsViewer(page) {
  await login(page, USERS.viewer);
}

export async function loginAsWarehouse(page) {
  await login(page, USERS.warehouse);
}

export async function logout(page) {
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page).toHaveURL('/');
}
