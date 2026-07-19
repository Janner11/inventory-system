import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ReportsPage from '../../src/pages/ReportsPage';
import { getInventoryReport } from '../../src/services/reportService';

vi.mock('../../src/services/reportService', () => ({
  getInventoryReport: vi.fn(),
}));

const REPORT = {
  generatedAt: '2026-07-01T12:00:00',
  summary: {
    totalProducts: 20,
    activeProducts: 18,
    inactiveProducts: 2,
    belowMinStockProducts: 3,
    totalInventoryValue: 1500.5,
    totalStockMovements: 40,
  },
  criticalProducts: [
    { id: '1', sku: 'TEC-002', name: 'Mouse Logitech', quantity: 2, minStock: 10 },
  ],
  recentMovements: [
    {
      id: 'm1',
      productId: '1',
      productSku: 'TEC-002',
      productName: 'Mouse Logitech',
      type: 'ENTRY',
      quantity: 5,
      performedBy: 'admin@test.com',
      createdAt: '2026-07-01T09:00:00',
    },
  ],
  topProducts: [{ productId: '1', sku: 'TEC-002', name: 'Mouse Logitech', movementCount: 4 }],
};

function renderReportsPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ReportsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ReportsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('muestra los KPIs, productos críticos, movimientos recientes y más movidos del reporte', async () => {
    getInventoryReport.mockResolvedValue(REPORT);
    renderReportsPage();

    expect(await screen.findByText('20')).toBeInTheDocument(); // totalProducts
    expect(screen.getByText('US$1,500.50')).toBeInTheDocument();
    // "Mouse Logitech" aparece 3 veces (productos críticos, movimientos recientes, más movidos).
    expect(screen.getAllByText('Mouse Logitech')).toHaveLength(3);
    expect(screen.getByText('admin@test.com')).toBeInTheDocument();
    expect(screen.getByText('4 movimientos')).toBeInTheDocument();
  });

  it('muestra un error cuando la API del reporte falla', async () => {
    getInventoryReport.mockRejectedValue(new Error('403'));
    renderReportsPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo cargar el reporte');
  });
});
