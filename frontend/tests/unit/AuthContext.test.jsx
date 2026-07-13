import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../src/context/AuthContext';
import { useAuth } from '../../src/hooks/useAuth';
import keycloak from '../../src/services/keycloak';

vi.mock('../../src/services/keycloak', () => ({
  default: {
    init: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    updateToken: vi.fn(),
    token: null,
    tokenParsed: null,
  },
}));

function AuthConsumer() {
  const { isAuthenticated, isLoading, user, login, logout, hasScope } = useAuth();

  if (isLoading) {
    return <p>Cargando sesión...</p>;
  }

  return (
    <div>
      <p>autenticado: {String(isAuthenticated)}</p>
      <p>usuario: {user?.preferred_username ?? 'ninguno'}</p>
      <p>product:view: {String(hasScope('product:view'))}</p>
      <p>product:manage: {String(hasScope('product:manage'))}</p>
      <button onClick={login}>Iniciar sesión</button>
      <button onClick={logout}>Cerrar sesión</button>
    </div>
  );
}

function renderAuth() {
  return render(
    <AuthProvider>
      <AuthConsumer />
    </AuthProvider>,
  );
}

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    keycloak.token = null;
    keycloak.tokenParsed = null;
  });

  it('muestra el estado de carga mientras Keycloak inicializa', () => {
    keycloak.init.mockReturnValue(new Promise(() => {}));

    renderAuth();

    expect(screen.getByText('Cargando sesión...')).toBeInTheDocument();
  });

  it('expone isAuthenticated=false cuando no hay sesión activa', async () => {
    keycloak.init.mockResolvedValue(false);

    renderAuth();

    await waitFor(() => expect(screen.getByText('autenticado: false')).toBeInTheDocument());
    expect(screen.getByText('usuario: ninguno')).toBeInTheDocument();
  });

  it('expone isAuthenticated=true y los datos del usuario cuando hay sesión activa', async () => {
    keycloak.token = 'fake-access-token';
    keycloak.tokenParsed = {
      preferred_username: 'admin@test.com',
      resource_access: { 'inventario-backend': { roles: ['product:view', 'product:manage'] } },
    };
    keycloak.init.mockResolvedValue(true);

    renderAuth();

    await waitFor(() => expect(screen.getByText('autenticado: true')).toBeInTheDocument());
    expect(screen.getByText('usuario: admin@test.com')).toBeInTheDocument();
  });

  it('hasScope retorna true solo para los scopes presentes en el token', async () => {
    keycloak.token = 'fake-access-token';
    keycloak.tokenParsed = {
      preferred_username: 'viewer@test.com',
      resource_access: { 'inventario-backend': { roles: ['product:view'] } },
    };
    keycloak.init.mockResolvedValue(true);

    renderAuth();

    await waitFor(() => expect(screen.getByText('product:view: true')).toBeInTheDocument());
    expect(screen.getByText('product:manage: false')).toBeInTheDocument();
  });

  it('login() delega en keycloak.login()', async () => {
    keycloak.init.mockResolvedValue(false);
    renderAuth();
    await waitFor(() => expect(screen.getByText('autenticado: false')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }));

    expect(keycloak.login).toHaveBeenCalledTimes(1);
  });

  it('logout() delega en keycloak.logout() con el redirectUri actual', async () => {
    keycloak.init.mockResolvedValue(true);
    keycloak.token = 'fake-access-token';
    keycloak.tokenParsed = { preferred_username: 'admin@test.com' };
    renderAuth();
    await waitFor(() => expect(screen.getByText('autenticado: true')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));

    expect(keycloak.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin });
  });
});
