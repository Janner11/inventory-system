import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../fixtures/auth.js';

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('el dashboard carga los KPIs del resumen del inventario', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Resumen del inventario' })).toBeVisible();

    const kpiLabels = [
      'Productos totales',
      'Productos activos',
      'Productos inactivos',
      'Bajo stock mínimo',
      'Valor del inventario',
      'Movimientos de stock',
    ];

    for (const label of kpiLabels) {
      await expect(page.getByText(label)).toBeVisible({ timeout: 10_000 });
    }
  });

  test('el dashboard muestra accesos rápidos, productos en alerta y movimientos recientes', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Accesos rápidos' })).toBeVisible();
    await expect(page.getByRole('link', { name: /^Productos / })).toBeVisible();
    await expect(page.getByRole('link', { name: /^Stock / })).toBeVisible();

    await expect(page.getByRole('heading', { name: 'Productos en alerta' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Movimientos recientes' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Más movidos (últimos 30 días)' })).toBeVisible();

    await page.screenshot({ path: 'test-results/screenshots/dashboard-kpis.png', fullPage: true });
  });
});
