import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AuditPage from '../../src/pages/AuditPage';
import { getProductRevisions } from '../../src/services/auditService';
import { getProducts } from '../../src/services/productService';

vi.mock('../../src/services/auditService', () => ({
  getProductRevisions: vi.fn(),
}));

vi.mock('../../src/services/productService', () => ({
  getProducts: vi.fn(),
}));

const PRODUCTS = [
  { id: '1', sku: 'TEC-001', name: 'Laptop Dell Inspiron', category: 'Electronica', price: 1200, quantity: 10, minStock: 3, status: 'ACTIVE' },
  { id: '2', sku: 'OFI-001', name: 'Escritorio', category: 'Oficina', price: 150, quantity: 8, minStock: 2, status: 'ACTIVE' },
];

const REVISIONS = [
  {
    revisionNumber: 1,
    revisionTimestamp: '2026-07-01T10:00:00',
    revisionType: 'ADD',
    revisedBy: 'admin@test.com',
    id: '1',
    name: 'Laptop Dell Inspiron',
    sku: 'TEC-001',
    category: 'Electronica',
    price: 1000,
    quantity: 5,
    minStock: 3,
    status: 'ACTIVE',
  },
  {
    revisionNumber: 2,
    revisionTimestamp: '2026-07-02T11:00:00',
    revisionType: 'MOD',
    revisedBy: 'manager@test.com',
    id: '1',
    name: 'Laptop Dell Inspiron',
    sku: 'TEC-001',
    category: 'Electronica',
    price: 1200,
    quantity: 10,
    minStock: 3,
    status: 'ACTIVE',
  },
];

function renderAuditPage(initialEntry = '/audit') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <AuditPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AuditPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getProducts.mockResolvedValue({ content: PRODUCTS, totalElements: 2, totalPages: 1, number: 0, size: 100 });
    getProductRevisions.mockResolvedValue(REVISIONS);
  });

  it('sin producto seleccionado, invita a elegir uno y no llama a la API de revisiones', async () => {
    renderAuditPage();

    await screen.findByRole('option', { name: /TEC-001/ });

    expect(screen.getByText('Selecciona un producto para ver su historial de revisiones.')).toBeInTheDocument();
    expect(getProductRevisions).not.toHaveBeenCalled();
  });

  it('al elegir un producto, muestra su historial de revisiones', async () => {
    const user = userEvent.setup();
    renderAuditPage();

    await screen.findByRole('option', { name: /TEC-001/ });
    await user.selectOptions(screen.getByLabelText('Producto'), '1');

    expect(await screen.findByText('Creación')).toBeInTheDocument();
    expect(screen.getByText('Modificación')).toBeInTheDocument();
    expect(screen.getByText('admin@test.com')).toBeInTheDocument();
    expect(screen.getByText('manager@test.com')).toBeInTheDocument();
    expect(getProductRevisions).toHaveBeenCalledWith('1');
  });

  it('con productId en la URL (deep-link desde ProductDetailPage), carga el historial automáticamente', async () => {
    renderAuditPage('/audit?productId=1');

    expect(await screen.findByText('Creación')).toBeInTheDocument();
    expect(getProductRevisions).toHaveBeenCalledWith('1');
  });

  it('muestra un mensaje cuando el producto no tiene revisiones', async () => {
    getProductRevisions.mockResolvedValue([]);
    const user = userEvent.setup();
    renderAuditPage();

    await screen.findByRole('option', { name: /TEC-001/ });
    await user.selectOptions(screen.getByLabelText('Producto'), '1');

    expect(await screen.findByText('Este producto todavía no tiene revisiones registradas.')).toBeInTheDocument();
  });

  it('muestra un error si la API de revisiones falla', async () => {
    getProductRevisions.mockRejectedValue(new Error('403'));
    const user = userEvent.setup();
    renderAuditPage();

    await screen.findByRole('option', { name: /TEC-001/ });
    await user.selectOptions(screen.getByLabelText('Producto'), '1');

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo cargar el historial de revisiones');
  });
});
