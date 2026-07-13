import { expect, test } from '@playwright/test';
import { loginAsAdmin, loginAsViewer } from '../fixtures/auth.js';
import { ProductsPage } from '../pageObjects/ProductsPage.js';

test.describe('Permisos por rol en la UI', () => {
  test('viewer no ve el botón "Nuevo producto" ni las acciones de gestión en la lista', async ({ page }) => {
    await loginAsViewer(page);

    const productsPage = new ProductsPage(page);
    await productsPage.goto();

    await expect(productsPage.newProductLink).not.toBeVisible();

    const rowCount = await page.getByRole('row').count();
    if (rowCount > 1) {
      const firstDataRow = page.getByRole('row').nth(1);
      await expect(firstDataRow.getByRole('link', { name: 'Editar' })).not.toBeVisible();
      await expect(firstDataRow.getByRole('button', { name: 'Eliminar' })).not.toBeVisible();
      // "Ver" sí debe seguir disponible: viewer tiene product:view.
      await expect(firstDataRow.getByRole('link', { name: 'Ver' })).toBeVisible();
    }

    await page.screenshot({ path: 'test-results/screenshots/permissions-viewer-products.png' });
  });

  test('viewer no puede crear un producto navegando directamente a /products/new', async ({ page }) => {
    const sku = `PERM-${Date.now()}`;
    await loginAsViewer(page);

    await page.goto('/products/new');
    await expect(page.getByRole('heading', { name: 'Nuevo producto' })).toBeVisible();

    await page.fill('#name', 'Producto Sin Permiso');
    await page.fill('#sku', sku);
    await page.fill('#description', 'No debería crearse');
    await page.fill('#category', 'E2E-Permisos');
    await page.fill('#price', '10');
    await page.fill('#quantity', '5');
    await page.fill('#minStock', '1');
    await page.getByRole('button', { name: 'Guardar' }).click();

    // El backend rechaza con 403 (SCOPE_product:manage insuficiente) — la UI lo muestra como alerta.
    await expect(page.getByRole('alert')).toBeVisible({ timeout: 10_000 });
    await expect(page).toHaveURL(/\/products\/new/);
  });

  test('admin sí ve el botón "Nuevo producto" y las acciones de gestión', async ({ page }) => {
    await loginAsAdmin(page);

    const productsPage = new ProductsPage(page);
    await productsPage.goto();

    await expect(productsPage.newProductLink).toBeVisible();
  });
});
