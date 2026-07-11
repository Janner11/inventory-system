import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../fixtures/auth.js';

test.describe('Navegación entre secciones', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('el sidebar navega entre Dashboard, Productos y Stock', async ({ page }) => {
    const sidebar = page.getByRole('navigation', { name: 'Navegación principal' });

    await sidebar.getByRole('link', { name: 'Productos' }).click();
    await expect(page).toHaveURL(/\/products$/);
    await expect(page.getByRole('heading', { name: 'Productos' })).toBeVisible();

    await sidebar.getByRole('link', { name: 'Stock' }).click();
    await expect(page).toHaveURL(/\/stock$/);
    await expect(page.getByRole('heading', { name: 'Stock', exact: true })).toBeVisible();

    await sidebar.getByRole('link', { name: 'Dashboard' }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  });

  test('el enlace activo del sidebar tiene aria-current="page"', async ({ page }) => {
    const sidebar = page.getByRole('navigation', { name: 'Navegación principal' });

    await expect(sidebar.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page');

    await sidebar.getByRole('link', { name: 'Productos' }).click();
    await expect(sidebar.getByRole('link', { name: 'Productos' })).toHaveAttribute('aria-current', 'page');
  });

  test('el breadcrumb refleja la sección activa y permite volver a Productos', async ({ page }) => {
    await page.goto('/products/new');

    const breadcrumb = page.getByRole('navigation', { name: 'Ruta de navegación' });
    await expect(breadcrumb.getByRole('link', { name: 'Productos' })).toBeVisible();

    await breadcrumb.getByRole('link', { name: 'Productos' }).click();
    await expect(page).toHaveURL(/\/products$/);
  });

  test('los accesos rápidos del dashboard navegan a Productos y Stock', async ({ page }) => {
    await page.getByRole('link', { name: 'Ir a Productos' }).click();
    await expect(page).toHaveURL(/\/products$/);

    await page.goto('/dashboard');
    await page.getByRole('link', { name: 'Ir a Stock' }).click();
    await expect(page).toHaveURL(/\/stock$/);
  });
});
