import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import ProtectedRoute from '../../src/components/ProtectedRoute';
import { useAuth } from '../../src/hooks/useAuth';

vi.mock('../../src/hooks/useAuth');

function renderProtected() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route path="/" element={<p>Página de inicio</p>} />
        <Route element={<ProtectedRoute />}>
          <Route path="/dashboard" element={<p>Contenido protegido</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  it('muestra un estado de carga mientras se resuelve la autenticación', () => {
    useAuth.mockReturnValue({ isAuthenticated: false, isLoading: true });

    renderProtected();

    expect(screen.getByText('Cargando sesión...')).toBeInTheDocument();
    expect(screen.queryByText('Contenido protegido')).not.toBeInTheDocument();
  });

  it('redirige a la ruta pública si no hay sesión activa', () => {
    useAuth.mockReturnValue({ isAuthenticated: false, isLoading: false });

    renderProtected();

    expect(screen.getByText('Página de inicio')).toBeInTheDocument();
    expect(screen.queryByText('Contenido protegido')).not.toBeInTheDocument();
  });

  it('renderiza la ruta protegida si hay sesión activa', () => {
    useAuth.mockReturnValue({ isAuthenticated: true, isLoading: false });

    renderProtected();

    expect(screen.getByText('Contenido protegido')).toBeInTheDocument();
  });
});
