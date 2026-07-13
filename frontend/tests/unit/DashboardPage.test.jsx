import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import AppShell from '../../src/components/layout/AppShell';
import ProtectedRoute from '../../src/components/ProtectedRoute';
import { useAuth } from '../../src/hooks/useAuth';
import DashboardPage from '../../src/pages/DashboardPage';
import ProductsPage from '../../src/pages/ProductsPage';
import StockPage from '../../src/pages/StockPage';

vi.mock('../../src/hooks/useAuth');

vi.mock('../../src/services/productService', () => ({
  getProducts: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
  getCriticalProducts: vi.fn().mockResolvedValue([]),
}));

const { mockSummary, mockCriticalProducts, mockRecentMovements, mockTopProducts } = vi.hoisted(() => ({
  mockSummary: {
    totalProducts: 10,
    activeProducts: 8,
    inactiveProducts: 2,
    belowMinStockProducts: 1,
    totalInventoryValue: 1500.5,
    totalStockMovements: 20,
  },
  mockCriticalProducts: [{ id: 'p1', name: 'Mouse', sku: 'MOU-001', quantity: 1, minStock: 5 }],
  mockRecentMovements: [
    {
      id: 'm1',
      productId: 'p1',
      productSku: 'MOU-001',
      productName: 'Mouse',
      type: 'ENTRY',
      quantity: 5,
      performedBy: 'admin@test.com',
      createdAt: '2026-07-10T10:00:00',
    },
  ],
  mockTopProducts: [{ productId: 'p1', sku: 'MOU-001', name: 'Mouse', movementCount: 3 }],
}));

vi.mock('../../src/services/dashboardService', () => ({
  getSummary: vi.fn().mockResolvedValue(mockSummary),
  getCriticalProducts: vi.fn().mockResolvedValue(mockCriticalProducts),
  getRecentMovements: vi.fn().mockResolvedValue(mockRecentMovements),
  getTopProducts: vi.fn().mockResolvedValue(mockTopProducts),
}));

const authenticatedUser = {
  preferred_username: 'admin@test.com',
  resource_access: {
    'inventario-backend': {
      roles: ['product:view', 'product:manage'],
    },
  },
};

function renderDashboard() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route path="/" element={<p>Página de inicio</p>} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppShell />}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/products" element={<ProductsPage />} />
              <Route path="/stock" element={<StockPage />} />
            </Route>
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('DashboardPage', () => {
  it('muestra la información del usuario autenticado y los accesos rápidos', () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: authenticatedUser,
      logout: vi.fn(),
      hasScope: () => true,
    });

    renderDashboard();

    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getAllByText('admin@test.com').length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Ir a Productos' })).toHaveAttribute('href', '/products');
    expect(screen.getByRole('link', { name: 'Ir a Stock' })).toHaveAttribute('href', '/stock');
  });

  it('redirige a la página de inicio si no hay sesión activa', () => {
    useAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      logout: vi.fn(),
      hasScope: () => true,
    });

    renderDashboard();

    expect(screen.getByText('Página de inicio')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Dashboard' })).not.toBeInTheDocument();
  });

  it('permite cerrar sesión desde el dashboard', async () => {
    const logout = vi.fn();
    useAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: authenticatedUser,
      logout,
      hasScope: () => true,
    });

    renderDashboard();

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));

    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('navega al módulo de Productos mediante el acceso rápido', async () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: authenticatedUser,
      logout: vi.fn(),
      hasScope: () => true,
    });

    renderDashboard();

    await userEvent.click(screen.getByRole('link', { name: 'Ir a Productos' }));

    expect(screen.getByRole('heading', { name: 'Productos' })).toBeInTheDocument();
  });

  it('navega al módulo de Stock mediante el acceso rápido', async () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: authenticatedUser,
      logout: vi.fn(),
      hasScope: () => true,
    });

    renderDashboard();

    await userEvent.click(screen.getByRole('link', { name: 'Ir a Stock' }));

    expect(screen.getByRole('heading', { name: 'Stock' })).toBeInTheDocument();
  });

  it('muestra los KPIs del resumen del inventario', async () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: authenticatedUser,
      logout: vi.fn(),
      hasScope: () => true,
    });

    renderDashboard();

    expect(await screen.findByText('10')).toBeInTheDocument();
    expect(screen.getByText('Productos totales')).toBeInTheDocument();
    expect(screen.getByText('US$1,500.50')).toBeInTheDocument();
  });

  it('muestra la tabla de productos en alerta y el widget de movimientos recientes', async () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: authenticatedUser,
      logout: vi.fn(),
      hasScope: () => true,
    });

    renderDashboard();

    expect((await screen.findAllByRole('link', { name: 'Mouse' })).length).toBeGreaterThan(0);
    expect(screen.getByText('Entrada')).toBeInTheDocument();
    expect(screen.getByText('+5')).toBeInTheDocument();
  });

  it('muestra el ranking de productos más movidos', async () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: authenticatedUser,
      logout: vi.fn(),
      hasScope: () => true,
    });

    renderDashboard();

    await waitFor(() => expect(screen.getByText('3 movimientos')).toBeInTheDocument());
  });
});
