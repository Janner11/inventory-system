import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../fixtures/auth.js';
import { ProductsPage } from '../pageObjects/ProductsPage.js';
import { StockPage } from '../pageObjects/StockPage.js';

test.describe('Movimientos de stock automatizados', () => {
  let sku;
  let productsPage;
  let stockPage;

  test.beforeEach(async ({ page }) => {
    sku = `STK-${Date.now()}`;
    productsPage = new ProductsPage(page);
    stockPage = new StockPage(page);

    await loginAsAdmin(page);

    // Producto base para los movimientos (quantity=10, minStock=5 — no arranca en alerta).
    await productsPage.createProduct({
      name: 'Producto Stock E2E',
      sku,
      description: 'Producto para pruebas de stock',
      category: 'E2E-Stock',
      price: 15,
      quantity: 10,
      minStock: 5,
    });
  });

  test('registrar entrada incrementa el stock y aparece en el historial', async ({ page }) => {
    await stockPage.goto();
    const productLabel = await stockPage.productOptionLabel(sku);

    await stockPage.registerMovement({
      type: 'ENTRY',
      productLabel,
      quantity: 5,
      reason: 'Entrada E2E',
    });

    const row = stockPage.historyRowBySku(sku);
    await expect(row).toBeVisible({ timeout: 10_000 });
    await expect(row.getByText('Entrada', { exact: true })).toBeVisible();
    await expect(row.getByText('+5', { exact: true })).toBeVisible();
    await page.screenshot({ path: 'test-results/screenshots/stock-entry.png' });
  });

  test('registrar salida decrementa el stock y aparece en el historial', async ({ page }) => {
    await stockPage.goto();
    const productLabel = await stockPage.productOptionLabel(sku);

    await stockPage.registerMovement({
      type: 'EXIT',
      productLabel,
      quantity: 3,
      reason: 'Salida E2E',
    });

    const row = stockPage.historyRowBySku(sku);
    await expect(row).toBeVisible({ timeout: 10_000 });
    await expect(row.getByText('Salida', { exact: true })).toBeVisible();
    await expect(row.getByText('-3', { exact: true })).toBeVisible();
    await page.screenshot({ path: 'test-results/screenshots/stock-exit.png' });
  });

  test('salida con cantidad mayor al stock disponible muestra un error', async ({ page }) => {
    await stockPage.goto();
    const productLabel = await stockPage.productOptionLabel(sku);

    await stockPage.registerMovement({
      type: 'EXIT',
      productLabel,
      quantity: 9999,
      reason: 'Salida inválida E2E',
    });

    await expect(page.getByRole('alert')).toBeVisible({ timeout: 10_000 });
  });

  test('ajustar stock por debajo del mínimo genera una alerta de stock bajo', async ({ page }) => {
    await stockPage.goto();
    const productLabel = await stockPage.productOptionLabel(sku);

    await stockPage.registerMovement({
      type: 'ADJUSTMENT',
      productLabel,
      newQuantity: 1,
      reason: 'Ajuste E2E bajo mínimo',
    });

    const row = stockPage.historyRowBySku(sku);
    await expect(row).toBeVisible({ timeout: 10_000 });
    await expect(row.getByText('Ajuste', { exact: true })).toBeVisible();

    // Recargar para confirmar que la alerta de stock bajo (quantity < minStock) refleja el ajuste.
    await stockPage.goto();
    await expect(stockPage.alertsSection.getByText(sku)).toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: 'test-results/screenshots/stock-alert.png' });
  });

  test('el historial de movimientos se muestra paginado', async ({ page }) => {
    await stockPage.goto();
    await expect(page.getByRole('navigation', { name: 'Paginación de productos' })).toBeVisible();
  });
});
