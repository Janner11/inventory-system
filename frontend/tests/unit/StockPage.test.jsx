import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import StockPage from '../../src/pages/StockPage';
import { useAuth } from '../../src/hooks/useAuth';
import { getProducts } from '../../src/services/productService';
import { adjustStock, getMovements, getStockAlerts, registerEntry, registerExit } from '../../src/services/stockService';

vi.mock('../../src/hooks/useAuth');

vi.mock('../../src/services/productService', () => ({
  getProducts: vi.fn(),
  getCriticalProducts: vi.fn(),
  deleteProduct: vi.fn(),
}));

vi.mock('../../src/services/stockService', () => ({
  getMovements: vi.fn(),
  getMovementsByProduct: vi.fn(),
  getStockAlerts: vi.fn(),
  registerEntry: vi.fn(),
  registerExit: vi.fn(),
  adjustStock: vi.fn(),
}));

const PRODUCTS = [
  { id: 'p1', sku: 'TEC-001', name: 'Laptop Dell', category: 'Electronica', price: 1200, quantity: 10, minStock: 3, status: 'ACTIVE' },
  { id: 'p2', sku: 'TEC-002', name: 'Mouse Logitech', category: 'Electronica', price: 25, quantity: 2, minStock: 10, status: 'ACTIVE' },
];

const ALERTS = [{ id: 'p2', sku: 'TEC-002', name: 'Mouse Logitech', quantity: 2, minStock: 10 }];

const MOVEMENT = {
  id: 'm1',
  productId: 'p1',
  productSku: 'TEC-001',
  productName: 'Laptop Dell',
  type: 'ENTRY',
  previousQuantity: 5,
  newQuantity: 10,
  quantity: 5,
  reason: 'Reposición',
  observations: '',
  performedBy: 'admin@test.com',
  createdAt: '2026-07-10T10:00:00',
};

function paginate(list, page = 0, size = 20) {
  const start = page * size;
  return { content: list.slice(start, start + size), totalElements: list.length, totalPages: Math.max(1, Math.ceil(list.length / size)), number: page, size };
}

function renderStockPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <StockPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();

  useAuth.mockReturnValue({
    user: { preferred_username: 'admin@test.com' },
  });

  getProducts.mockImplementation(async ({ page = 0, size = 20 } = {}) => paginate(PRODUCTS, page, size));
  getStockAlerts.mockResolvedValue(ALERTS);
  getMovements.mockImplementation(async ({ page = 0, size = 20 } = {}) => paginate([MOVEMENT], page, size));
  registerEntry.mockResolvedValue(MOVEMENT);
  registerExit.mockResolvedValue({ ...MOVEMENT, type: 'EXIT' });
  adjustStock.mockResolvedValue({ ...MOVEMENT, type: 'ADJUSTMENT' });
});

describe('StockPage - alertas', () => {
  it('muestra los productos en alerta de stock bajo', async () => {
    renderStockPage();

    expect(await screen.findByText('TEC-002')).toBeInTheDocument();
    expect(screen.getByText('Mouse Logitech')).toBeInTheDocument();
  });

  it('muestra un mensaje cuando no hay alertas', async () => {
    getStockAlerts.mockResolvedValue([]);
    renderStockPage();

    expect(await screen.findByText('No hay productos en alerta de stock bajo.')).toBeInTheDocument();
  });
});

describe('StockPage - historial de movimientos', () => {
  it('muestra el historial paginado', async () => {
    renderStockPage();

    const row = await screen.findByRole('row', { name: /TEC-001/ });
    expect(within(row).getByText('Entrada')).toBeInTheDocument();
    expect(screen.getByText('Página 1 de 1')).toBeInTheDocument();
  });

  it('pide la página 2 al backend al hacer click en Siguiente', async () => {
    getMovements.mockImplementation(async ({ page = 0, size = 20 } = {}) =>
      paginate([MOVEMENT, { ...MOVEMENT, id: 'm2' }, { ...MOVEMENT, id: 'm3' }, { ...MOVEMENT, id: 'm4' }, { ...MOVEMENT, id: 'm5' }, { ...MOVEMENT, id: 'm6' }], page, size),
    );
    const user = userEvent.setup();
    renderStockPage();

    await screen.findByText('Página 1 de 2');
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    await waitFor(() => expect(getMovements).toHaveBeenCalledWith(expect.objectContaining({ page: 1 })));
  });
});

describe('StockPage - registrar movimiento', () => {
  it('registra una entrada con el usuario autenticado como performedBy', async () => {
    const user = userEvent.setup();
    renderStockPage();

    await screen.findByText('TEC-002');

    await user.selectOptions(screen.getByLabelText('Producto'), 'p1');
    await user.type(screen.getByLabelText('Cantidad'), '5');
    await user.click(screen.getByRole('button', { name: 'Registrar movimiento' }));

    await waitFor(() => expect(registerEntry).toHaveBeenCalled());
    expect(registerEntry.mock.calls[0][0]).toEqual(
      expect.objectContaining({ productId: 'p1', quantity: 5, performedBy: 'admin@test.com' }),
    );
  });

  it('registra una salida cuando el tipo es Salida', async () => {
    const user = userEvent.setup();
    renderStockPage();

    await screen.findByText('TEC-002');

    await user.selectOptions(screen.getByLabelText('Tipo de movimiento'), 'EXIT');
    await user.selectOptions(screen.getByLabelText('Producto'), 'p1');
    await user.type(screen.getByLabelText('Cantidad'), '2');
    await user.click(screen.getByRole('button', { name: 'Registrar movimiento' }));

    await waitFor(() => expect(registerExit).toHaveBeenCalled());
    expect(registerEntry).not.toHaveBeenCalled();
  });

  it('muestra "Nueva cantidad" en vez de "Cantidad" cuando el tipo es Ajuste, y llama a adjustStock', async () => {
    const user = userEvent.setup();
    renderStockPage();

    await screen.findByText('TEC-002');

    await user.selectOptions(screen.getByLabelText('Tipo de movimiento'), 'ADJUSTMENT');
    expect(screen.queryByLabelText('Cantidad')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Nueva cantidad')).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Producto'), 'p1');
    await user.type(screen.getByLabelText('Nueva cantidad'), '20');
    await user.click(screen.getByRole('button', { name: 'Registrar movimiento' }));

    await waitFor(() => expect(adjustStock).toHaveBeenCalled());
    expect(adjustStock.mock.calls[0][0]).toEqual(
      expect.objectContaining({ productId: 'p1', newQuantity: 20, performedBy: 'admin@test.com' }),
    );
  });

  it('muestra un error de validación si no se selecciona producto', async () => {
    const user = userEvent.setup();
    renderStockPage();

    await screen.findByText('TEC-002');

    await user.type(screen.getByLabelText('Cantidad'), '5');
    await user.click(screen.getByRole('button', { name: 'Registrar movimiento' }));

    expect(await screen.findByText('El producto es obligatorio')).toBeInTheDocument();
    expect(registerEntry).not.toHaveBeenCalled();
  });

  it('muestra el error de la API cuando el stock es insuficiente (422)', async () => {
    registerExit.mockRejectedValue({
      response: { status: 422, data: { message: 'Stock insuficiente para el producto TEC-001' } },
    });
    const user = userEvent.setup();
    renderStockPage();

    await screen.findByText('TEC-002');

    await user.selectOptions(screen.getByLabelText('Tipo de movimiento'), 'EXIT');
    await user.selectOptions(screen.getByLabelText('Producto'), 'p1');
    await user.type(screen.getByLabelText('Cantidad'), '999');
    await user.click(screen.getByRole('button', { name: 'Registrar movimiento' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Stock insuficiente para el producto TEC-001');
  });
});
