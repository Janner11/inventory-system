import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Sidebar from '../../src/components/layout/Sidebar';
import { useAuth } from '../../src/hooks/useAuth';

vi.mock('../../src/hooks/useAuth');

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Sidebar />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  useAuth.mockReturnValue({ hasScope: () => false });
});

describe('Sidebar', () => {
  it('muestra los enlaces a Dashboard, Productos y Stock', () => {
    renderAt('/dashboard');

    const nav = screen.getByRole('navigation', { name: 'Navegación principal' });
    expect(nav).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('href', '/dashboard');
    expect(screen.getByRole('link', { name: 'Productos' })).toHaveAttribute('href', '/products');
    expect(screen.getByRole('link', { name: 'Stock' })).toHaveAttribute('href', '/stock');
  });

  it('marca el enlace activo con aria-current="page"', () => {
    renderAt('/products');

    expect(screen.getByRole('link', { name: 'Productos' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Dashboard' })).not.toHaveAttribute('aria-current');
  });

  it('oculta el enlace a Usuarios sin el scope user:manage', () => {
    renderAt('/dashboard');

    expect(screen.queryByRole('link', { name: 'Usuarios' })).not.toBeInTheDocument();
  });

  it('muestra el enlace a Usuarios con el scope user:manage', () => {
    useAuth.mockReturnValue({ hasScope: (scope) => scope === 'user:manage' });
    renderAt('/dashboard');

    expect(screen.getByRole('link', { name: 'Usuarios' })).toHaveAttribute('href', '/users');
  });
});
