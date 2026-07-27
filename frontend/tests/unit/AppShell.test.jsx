import { render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import AppShell from '../../src/components/layout/AppShell';
import { useAuth } from '../../src/hooks/useAuth';

vi.mock('../../src/hooks/useAuth');

function renderAt(path) {
  useAuth.mockReturnValue({
    user: { preferred_username: 'admin@test.com' },
    logout: vi.fn(),
    hasScope: () => false,
  });

  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<h1>Dashboard</h1>} />
          <Route path="/products" element={<h1>Productos</h1>} />
          <Route path="/products/:id" element={<h1>Detalle de producto</h1>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('AppShell', () => {
  it('renderiza Navbar, Sidebar, Breadcrumb y el contenido de la ruta activa', () => {
    renderAt('/dashboard');

    expect(screen.getByText('Sistema de Inventarios')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
  });

  it('el breadcrumb refleja la ruta anidada activa', () => {
    renderAt('/products/123e4567-e89b-12d3-a456-426614174000');

    const breadcrumb = screen.getByRole('navigation', { name: 'Ruta de navegación' });
    expect(breadcrumb).toBeInTheDocument();
    expect(within(breadcrumb).getByRole('link', { name: 'Productos' })).toHaveAttribute('href', '/products');
    expect(within(breadcrumb).getByText('Detalle')).toHaveAttribute('aria-current', 'page');
  });
});
