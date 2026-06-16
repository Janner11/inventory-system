import { expect, test } from '@playwright/test';

async function loginAsAdmin(page) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.waitForURL(/realms\/inventario/);
  await page.fill('#username', 'admin@test.com');
  await page.fill('#password', 'admin123');
  await page.click('#kc-login');
  await page.waitForURL('**/dashboard');
}

test('login automatizado redirige a /dashboard', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Sistema de Gestión de Inventarios' })).toBeVisible();

  await page.getByRole('button', { name: 'Iniciar sesión' }).click();

  await page.waitForURL(/realms\/inventario/);
  await page.fill('#username', 'admin@test.com');
  await page.fill('#password', 'admin123');
  await page.click('#kc-login');

  await page.waitForURL('**/dashboard');
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await expect(page.getByText('Bienvenido')).toBeVisible();
});

test('post-login: navbar y sidebar visibles con datos del usuario', async ({ page }) => {
  await loginAsAdmin(page);

  await expect(page.getByText('Sistema de Inventarios')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();

  // Scope sidebar links to the <nav aria-label="Navegación principal">
  const sidebar = page.getByRole('navigation', { name: 'Navegación principal' });
  await expect(sidebar.getByRole('link', { name: 'Dashboard' })).toBeVisible();
  await expect(sidebar.getByRole('link', { name: 'Productos' })).toBeVisible();
});

test('logout regresa a la pantalla de inicio', async ({ page }) => {
  await loginAsAdmin(page);

  await page.getByRole('button', { name: 'Cerrar sesión' }).click();

  await expect(page).toHaveURL('/');
  await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeVisible();
});
