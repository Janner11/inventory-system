import { expect } from '@playwright/test';

/** Page Object Model para /products, /products/new y /products/:id/edit. */
export class ProductsPage {
  constructor(page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/products');
    await expect(this.page.getByRole('heading', { name: 'Productos' })).toBeVisible();
  }

  async gotoNew() {
    await this.page.goto('/products/new');
    await expect(this.page.getByRole('heading', { name: 'Nuevo producto' })).toBeVisible();
  }

  get newProductLink() {
    return this.page.getByRole('link', { name: 'Nuevo producto' });
  }

  get searchInput() {
    return this.page.locator('#product-search');
  }

  get categorySelect() {
    return this.page.locator('#product-category');
  }

  get lowStockOnlyCheckbox() {
    return this.page.locator('#product-low-stock');
  }

  rowBySku(sku) {
    return this.page.getByRole('row').filter({ hasText: sku });
  }

  async filterBySku(sku) {
    await this.searchInput.fill(sku);
    await expect(this.page.getByText(sku)).toBeVisible({ timeout: 10_000 });
  }

  async fillForm({ name, sku, description, category, price, quantity, minStock }) {
    await this.page.fill('#name', name);
    await this.page.fill('#sku', sku);
    await this.page.fill('#description', description);
    await this.page.fill('#category', category);
    await this.page.fill('#price', String(price));
    await this.page.fill('#quantity', String(quantity));
    await this.page.fill('#minStock', String(minStock));
  }

  async submit() {
    await this.page.getByRole('button', { name: 'Guardar' }).click();
  }

  async createProduct(product) {
    await this.gotoNew();
    await this.fillForm(product);
    await this.submit();
    await this.page.waitForURL('**/products');
    await this.filterBySku(product.sku);
  }

  async editProduct(sku, newName) {
    const row = this.rowBySku(sku);
    await row.getByRole('link', { name: 'Editar' }).click();
    await this.page.waitForURL(/\/products\/.*\/edit/);
    await expect(this.page.getByRole('heading', { name: 'Editar producto' })).toBeVisible();
    await expect(this.page.locator('#name')).not.toHaveValue('');
    await this.page.fill('#name', newName);
    await this.submit();
    await this.page.waitForURL('**/products');
  }

  async deleteProduct(sku) {
    const row = this.rowBySku(sku);
    await row.getByRole('button', { name: 'Eliminar' }).click();
    // FRONT-009: window.confirm() fue reemplazado por ConfirmDialog, un modal propio -
    // ya no hay diálogo nativo del navegador que aceptar con page.on('dialog', ...).
    await this.page.getByRole('alertdialog').getByRole('button', { name: 'Eliminar' }).click();
  }
}
