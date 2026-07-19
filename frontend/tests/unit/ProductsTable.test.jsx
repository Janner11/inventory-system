import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ProductsTable from '../../src/components/products/ProductsTable';
import { deleteProduct } from '../../src/services/productService';

vi.mock('../../src/services/productService', () => ({
  deleteProduct: vi.fn(),
}));

function buildProduct(overrides = {}) {
  return {
    id: '1',
    sku: 'TEC-001',
    name: 'Laptop Dell Inspiron',
    category: 'Electronica',
    price: 1200,
    quantity: 10,
    minStock: 3,
    status: 'ACTIVE',
    ...overrides,
  };
}

function renderTable(products, canManage = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ProductsTable products={products} canManage={canManage} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ProductsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    deleteProduct.mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // FRONT-006: antes de este ticket, el dialogo de confirmacion no mencionaba el stock
  // disponible ("¿Eliminar el producto X?"), sin importar si el producto tenia 0 o 500
  // unidades - un usuario podia desactivar sin querer un producto con inventario real.
  it('advierte el stock disponible en el mensaje de confirmación cuando quantity > 0', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderTable([buildProduct({ name: 'Laptop Dell Inspiron', quantity: 500 })]);

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(confirmSpy).toHaveBeenCalledWith(
      '¿Eliminar el producto "Laptop Dell Inspiron"? Todavía tiene 500 unidades en stock.',
    );
  });

  it('no menciona el stock en el mensaje de confirmación cuando quantity === 0', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderTable([buildProduct({ name: 'Producto agotado', quantity: 0 })]);

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(confirmSpy).toHaveBeenCalledWith('¿Eliminar el producto "Producto agotado"?');
  });

  it('elimina el producto cuando se confirma el diálogo', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderTable([buildProduct({ id: '42', quantity: 5 })]);

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(deleteProduct).toHaveBeenCalledWith('42');
  });

  it('no elimina el producto cuando se cancela el diálogo', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderTable([buildProduct({ quantity: 5 })]);

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(deleteProduct).not.toHaveBeenCalled();
  });

  it('no muestra las acciones de gestión cuando canManage es false', () => {
    renderTable([buildProduct()], false);

    expect(screen.queryByRole('button', { name: 'Eliminar' })).not.toBeInTheDocument();
    expect(screen.queryByText('Editar')).not.toBeInTheDocument();
  });
});
