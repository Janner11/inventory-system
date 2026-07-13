import { expect } from '@playwright/test';

/** Page Object Model para /stock (alertas, registro de movimientos, historial). */
export class StockPage {
  constructor(page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/stock');
    await expect(this.page.getByRole('heading', { name: 'Stock', exact: true })).toBeVisible();
  }

  get productSelect() {
    return this.page.locator('#productId');
  }

  get typeSelect() {
    return this.page.locator('#type');
  }

  get quantityInput() {
    return this.page.locator('#quantity');
  }

  get newQuantityInput() {
    return this.page.locator('#newQuantity');
  }

  get reasonInput() {
    return this.page.locator('#reason');
  }

  get submitButton() {
    return this.page.getByRole('button', { name: 'Registrar movimiento' });
  }

  get alertsSection() {
    return this.page.locator('section', { hasText: 'Alertas de stock bajo' });
  }

  get historySection() {
    return this.page.locator('section', { hasText: 'Historial de movimientos' });
  }

  /** Fila del historial que contiene el SKU dado (evita colisiones con historial acumulado de otros SKUs). */
  historyRowBySku(sku) {
    return this.historySection.getByRole('row').filter({ hasText: sku });
  }

  async registerMovement({ type, productLabel, quantity, newQuantity, reason }) {
    await this.typeSelect.selectOption(type);
    await this.productSelect.selectOption({ label: productLabel });
    if (type === 'ADJUSTMENT') {
      await this.newQuantityInput.fill(String(newQuantity));
    } else {
      await this.quantityInput.fill(String(quantity));
    }
    if (reason) {
      await this.reasonInput.fill(reason);
    }
    await this.submitButton.click();
  }

  /**
   * Devuelve la opción del selector de producto que empieza con el SKU dado.
   * El fetch de useProducts({ size: 100 }) puede seguir en curso justo después de
   * stockPage.goto() (recién se creó el producto en el test) — se espera con polling
   * en vez de leer el <select> una sola vez, que solo tendría el placeholder inicial.
   */
  async productOptionLabel(sku) {
    let found = null;
    await expect
      .poll(
        async () => {
          const options = await this.productSelect.locator('option').allTextContents();
          found = options.find((label) => label.startsWith(sku)) ?? null;
          return found;
        },
        {
          message: `No se encontró un producto con SKU ${sku} en el selector de stock`,
          timeout: 10_000,
        },
      )
      .not.toBeNull();
    return found;
  }
}
