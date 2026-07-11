import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../fixtures/auth.js';
import { ProductsPage } from '../pageObjects/ProductsPage.js';

test.describe('CRUD de Producto automatizado', () => {
  let sku;
  let productsPage;

  test.beforeEach(async ({ page }) => {
    sku = `E2E-${Date.now()}`;
    productsPage = new ProductsPage(page);
    await loginAsAdmin(page);
  });

  test('crear producto muestra el nuevo producto en la lista', async ({ page }) => {
    await productsPage.createProduct({
      name: 'Producto E2E',
      sku,
      description: 'Creado por Playwright',
      category: 'E2E',
      price: 19.99,
      quantity: 30,
      minStock: 5,
    });

    await expect(productsPage.rowBySku(sku).getByText('Producto E2E', { exact: true })).toBeVisible();
    await page.screenshot({ path: 'test-results/screenshots/products-create.png' });
  });

  test('editar producto actualiza los datos en la lista', async ({ page }) => {
    await productsPage.createProduct({
      name: 'Producto E2E',
      sku,
      description: 'Creado por Playwright',
      category: 'E2E',
      price: 19.99,
      quantity: 30,
      minStock: 5,
    });

    await productsPage.editProduct(sku, 'Producto E2E Actualizado');

    await productsPage.filterBySku(sku);
    await expect(productsPage.rowBySku(sku).getByText('Producto E2E Actualizado', { exact: true })).toBeVisible();
    await page.screenshot({ path: 'test-results/screenshots/products-edit.png' });
  });

  test('eliminar producto lo quita de la lista', async ({ page }) => {
    await productsPage.createProduct({
      name: 'Producto E2E Para Borrar',
      sku,
      description: 'Creado por Playwright',
      category: 'E2E',
      price: 5.0,
      quantity: 10,
      minStock: 1,
    });

    await productsPage.deleteProduct(sku);

    await expect(productsPage.rowBySku(sku)).not.toBeVisible({ timeout: 10_000 });
    await page.screenshot({ path: 'test-results/screenshots/products-delete.png' });
  });

  test('CRUD completo: crear, leer, editar y eliminar en un flujo', async ({ page }) => {
    // CREATE
    await productsPage.createProduct({
      name: 'Producto Flujo CRUD',
      sku,
      description: 'Test CRUD completo',
      category: 'E2E',
      price: 99.99,
      quantity: 20,
      minStock: 3,
    });

    // READ
    await expect(productsPage.rowBySku(sku).getByText('Producto Flujo CRUD', { exact: true })).toBeVisible();

    // UPDATE
    await productsPage.editProduct(sku, 'Producto Flujo CRUD v2');
    await productsPage.filterBySku(sku);
    await expect(productsPage.rowBySku(sku).getByText('Producto Flujo CRUD v2', { exact: true })).toBeVisible();

    // DELETE
    await productsPage.deleteProduct(sku);
    await expect(productsPage.rowBySku(sku)).not.toBeVisible({ timeout: 10_000 });
  });

  test('búsqueda y filtro por categoría acotan la lista de productos', async ({ page }) => {
    await productsPage.createProduct({
      name: 'Producto Filtrable',
      sku,
      description: 'Para probar filtros',
      category: 'E2E-Filtros',
      price: 10,
      quantity: 5,
      minStock: 1,
    });

    // El filtro de categoría por sí solo pagina de a 5 — con muchas corridas acumuladas de este
    // spec puede haber más de 5 productos con categoría "E2E-Filtros" y el nuevo caer en la página 2.
    // Se combina categoría + búsqueda por SKU único para acotar a exactamente 1 resultado, sin
    // depender de la posición de paginación.
    await productsPage.categorySelect.selectOption('E2E-Filtros');
    await productsPage.filterBySku(sku);
    await expect(productsPage.rowBySku(sku)).toBeVisible({ timeout: 10_000 });

    // Categoría que no matchea el producto: la fila desaparece de la lista.
    await productsPage.categorySelect.selectOption('E2E');
    await expect(productsPage.rowBySku(sku)).not.toBeVisible({ timeout: 10_000 });

    await productsPage.categorySelect.selectOption('');
    await productsPage.filterBySku(sku);
  });
});
