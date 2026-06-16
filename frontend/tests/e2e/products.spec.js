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

async function fillProductForm(page, { name, sku, description, category, price, quantity, minStock }) {
  await page.fill('#name', name);
  await page.fill('#sku', sku);
  await page.fill('#description', description);
  await page.fill('#category', category);
  await page.fill('#price', String(price));
  await page.fill('#quantity', String(quantity));
  await page.fill('#minStock', String(minStock));
}

async function filterBySku(page, sku) {
  await page.fill('#product-search', sku);
  await expect(page.getByText(sku)).toBeVisible({ timeout: 10_000 });
}

test.describe('CRUD de Producto automatizado', () => {
  let sku;

  test.beforeEach(async ({ page }) => {
    sku = `E2E-${Date.now()}`;
    await loginAsAdmin(page);
  });

  test('crear producto muestra el nuevo producto en la lista', async ({ page }) => {
    await page.goto('/products/new');
    await expect(page.getByRole('heading', { name: 'Nuevo producto' })).toBeVisible();

    await fillProductForm(page, {
      name: 'Producto E2E',
      sku,
      description: 'Creado por Playwright',
      category: 'E2E',
      price: 19.99,
      quantity: 30,
      minStock: 5,
    });
    await page.getByRole('button', { name: 'Guardar' }).click();

    await page.waitForURL('**/products');
    await filterBySku(page, sku);
    await expect(page.getByText('Producto E2E')).toBeVisible();
  });

  test('editar producto actualiza los datos en la lista', async ({ page }) => {
    // Crea el producto
    await page.goto('/products/new');
    await fillProductForm(page, {
      name: 'Producto E2E',
      sku,
      description: 'Creado por Playwright',
      category: 'E2E',
      price: 19.99,
      quantity: 30,
      minStock: 5,
    });
    await page.getByRole('button', { name: 'Guardar' }).click();
    await page.waitForURL('**/products');

    // Filtra para encontrar la fila del producto
    await filterBySku(page, sku);
    const row = page.getByRole('row').filter({ hasText: sku });
    await row.getByRole('link', { name: 'Editar' }).click();
    await page.waitForURL(/\/products\/.*\/edit/);

    await expect(page.getByRole('heading', { name: 'Editar producto' })).toBeVisible();
    await expect(page.locator('#name')).not.toHaveValue('');

    await page.fill('#name', 'Producto E2E Actualizado');
    await page.getByRole('button', { name: 'Guardar' }).click();

    await page.waitForURL('**/products');
    await filterBySku(page, sku);
    await expect(page.getByText('Producto E2E Actualizado')).toBeVisible();
  });

  test('eliminar producto lo quita de la lista', async ({ page }) => {
    // Crea el producto
    await page.goto('/products/new');
    await fillProductForm(page, {
      name: 'Producto E2E Para Borrar',
      sku,
      description: 'Creado por Playwright',
      category: 'E2E',
      price: 5.00,
      quantity: 10,
      minStock: 1,
    });
    await page.getByRole('button', { name: 'Guardar' }).click();
    await page.waitForURL('**/products');
    await filterBySku(page, sku);

    // Elimina el producto
    page.once('dialog', (dialog) => dialog.accept());
    const row = page.getByRole('row').filter({ hasText: sku });
    await row.getByRole('button', { name: 'Eliminar' }).click();

    await expect(page.getByText(sku)).not.toBeVisible({ timeout: 10_000 });
  });

  test('CRUD completo: crear, leer, editar y eliminar en un flujo', async ({ page }) => {
    // CREATE
    await page.goto('/products/new');
    await fillProductForm(page, {
      name: 'Producto Flujo CRUD',
      sku,
      description: 'Test CRUD completo',
      category: 'E2E',
      price: 99.99,
      quantity: 20,
      minStock: 3,
    });
    await page.getByRole('button', { name: 'Guardar' }).click();
    await page.waitForURL('**/products');

    // READ — filtra y verifica que aparece en la lista
    await filterBySku(page, sku);
    await expect(page.getByText('Producto Flujo CRUD')).toBeVisible();

    // UPDATE
    const row = page.getByRole('row').filter({ hasText: sku });
    await row.getByRole('link', { name: 'Editar' }).click();
    await page.waitForURL(/\/products\/.*\/edit/);
    await expect(page.locator('#name')).not.toHaveValue('');
    await page.fill('#name', 'Producto Flujo CRUD v2');
    await page.getByRole('button', { name: 'Guardar' }).click();
    await page.waitForURL('**/products');
    await filterBySku(page, sku);
    await expect(page.getByText('Producto Flujo CRUD v2')).toBeVisible();

    // DELETE
    page.once('dialog', (dialog) => dialog.accept());
    const updatedRow = page.getByRole('row').filter({ hasText: sku });
    await updatedRow.getByRole('button', { name: 'Eliminar' }).click();
    await expect(page.getByText(sku)).not.toBeVisible({ timeout: 10_000 });
  });
});
