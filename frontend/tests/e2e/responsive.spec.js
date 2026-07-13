import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../fixtures/auth.js';

const MOBILE_VIEWPORT = { width: 390, height: 844 };

test.describe('Responsive — viewport mobile 390px', () => {
  test.use({ viewport: MOBILE_VIEWPORT });

  test('la pantalla de login es usable en mobile', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Sistema de Gestión de Inventarios' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeVisible();

    await page.screenshot({ path: 'test-results/screenshots/responsive-login-mobile.png' });
  });

  test('el dashboard, navbar y sidebar son usables en mobile', async ({ page }) => {
    await loginAsAdmin(page);

    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await expect(page.getByText('Sistema de Inventarios')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();

    const sidebar = page.getByRole('navigation', { name: 'Navegación principal' });
    await expect(sidebar.getByRole('link', { name: 'Productos' })).toBeVisible();
    await expect(sidebar.getByRole('link', { name: 'Stock' })).toBeVisible();

    await page.screenshot({ path: 'test-results/screenshots/responsive-dashboard-mobile.png', fullPage: true });
  });

  test('la lista de productos con scroll horizontal es usable en mobile', async ({ page }) => {
    await loginAsAdmin(page);

    await page.goto('/products');
    await expect(page.getByRole('heading', { name: 'Productos' })).toBeVisible();
    await expect(page.locator('#product-search')).toBeVisible();
    await expect(page.getByRole('table')).toBeVisible();

    await page.screenshot({ path: 'test-results/screenshots/responsive-products-mobile.png', fullPage: true });
  });

  test('el módulo de stock es usable en mobile', async ({ page }) => {
    await loginAsAdmin(page);

    await page.goto('/stock');
    await expect(page.getByRole('heading', { name: 'Stock', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Registrar movimiento' })).toBeVisible();

    await page.screenshot({ path: 'test-results/screenshots/responsive-stock-mobile.png', fullPage: true });
  });
});
