import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ProductsTable from '../../src/components/products/ProductsTable';
import { ToastProvider } from '../../src/context/ToastContext';
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
      <ToastProvider>
        <MemoryRouter>
          <ProductsTable products={products} canManage={canManage} />
        </MemoryRouter>
      </ToastProvider>
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
  // FRONT-009: el dialogo nativo del navegador (window.confirm) fue reemplazado por
  // ConfirmDialog, un componente propio - el mensaje condicional de FRONT-006 se preserva.
  it('advierte el stock disponible en el mensaje de confirmación cuando quantity > 0', async () => {
    renderTable([buildProduct({ name: 'Laptop Dell Inspiron', quantity: 500 })]);

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));

    const dialog = screen.getByRole('alertdialog');
    expect(
      within(dialog).getByText('¿Eliminar el producto "Laptop Dell Inspiron"? Todavía tiene 500 unidades en stock.'),
    ).toBeInTheDocument();
  });

  it('no menciona el stock en el mensaje de confirmación cuando quantity === 0', async () => {
    renderTable([buildProduct({ name: 'Producto agotado', quantity: 0 })]);

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));

    const dialog = screen.getByRole('alertdialog');
    expect(within(dialog).getByText('¿Eliminar el producto "Producto agotado"?')).toBeInTheDocument();
  });

  it('elimina el producto y muestra un toast de éxito cuando se confirma el diálogo', async () => {
    renderTable([buildProduct({ id: '42', quantity: 5 })]);

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));
    const dialog = screen.getByRole('alertdialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Eliminar' }));

    expect(deleteProduct).toHaveBeenCalledWith('42');
    expect(await screen.findByText('Producto eliminado correctamente.')).toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('no elimina el producto cuando se cancela el diálogo', async () => {
    renderTable([buildProduct({ quantity: 5 })]);

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));
    const dialog = screen.getByRole('alertdialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    expect(deleteProduct).not.toHaveBeenCalled();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('cierra el diálogo de confirmación con la tecla Escape sin eliminar', async () => {
    renderTable([buildProduct({ quantity: 5 })]);

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();

    await userEvent.keyboard('{Escape}');

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(deleteProduct).not.toHaveBeenCalled();
  });

  it('muestra un toast de error cuando falla la eliminación', async () => {
    deleteProduct.mockRejectedValue(new Error('Network Error'));
    renderTable([buildProduct({ quantity: 5 })]);

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));
    const dialog = screen.getByRole('alertdialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Eliminar' }));

    expect(await screen.findByText('No se pudo eliminar el producto.')).toBeInTheDocument();
  });

  it('no muestra las acciones de gestión cuando canManage es false', () => {
    renderTable([buildProduct()], false);

    expect(screen.queryByRole('button', { name: 'Eliminar' })).not.toBeInTheDocument();
    expect(screen.queryByText('Editar')).not.toBeInTheDocument();
  });
});
