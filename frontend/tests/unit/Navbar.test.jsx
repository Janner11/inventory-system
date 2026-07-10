import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import Navbar from '../../src/components/layout/Navbar';
import { useAuth } from '../../src/hooks/useAuth';

vi.mock('../../src/hooks/useAuth');

describe('Navbar', () => {
  it('muestra el nombre de la app y el usuario autenticado', () => {
    useAuth.mockReturnValue({
      user: { preferred_username: 'admin@test.com' },
      logout: vi.fn(),
    });

    render(<Navbar />);

    expect(screen.getByText('Sistema de Inventarios')).toBeInTheDocument();
    expect(screen.getByText('admin@test.com')).toBeInTheDocument();
  });

  it('el botón "Cerrar sesión" invoca logout()', async () => {
    const logout = vi.fn();
    useAuth.mockReturnValue({
      user: { preferred_username: 'admin@test.com' },
      logout,
    });

    render(<Navbar />);

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));

    expect(logout).toHaveBeenCalledTimes(1);
  });
});
