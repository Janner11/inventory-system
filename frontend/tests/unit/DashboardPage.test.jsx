import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
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
  getProducts: vi.fn().mockResolvedValue([]),
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
    });

    renderDashboard();

    await userEvent.click(screen.getByRole('link', { name: 'Ir a Stock' }));

    expect(screen.getByRole('heading', { name: 'Stock' })).toBeInTheDocument();
  });
});
