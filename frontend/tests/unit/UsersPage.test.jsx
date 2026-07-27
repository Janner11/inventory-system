import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import UsersPage from '../../src/pages/UsersPage';
import { ToastProvider } from '../../src/context/ToastContext';
import { createUser, getAssignableRoles, getUsers, setUserEnabled } from '../../src/services/userService';

vi.mock('../../src/services/userService', () => ({
  getUsers: vi.fn(),
  getAssignableRoles: vi.fn(),
  createUser: vi.fn(),
  setUserEnabled: vi.fn(),
}));

const ROLES = ['ADMIN', 'MANAGER', 'WAREHOUSE', 'VIEWER', 'AUDITOR'];

const USERS = [
  { id: 'u1', username: 'admin@test.com', email: 'admin@test.com', firstName: 'Admin', lastName: 'Test', enabled: true, role: 'ADMIN' },
  { id: 'u2', username: 'viewer@test.com', email: 'viewer@test.com', firstName: 'Viewer', lastName: 'Test', enabled: false, role: 'VIEWER' },
];

function renderUsersPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <UsersPage />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  getUsers.mockResolvedValue(USERS);
  getAssignableRoles.mockResolvedValue(ROLES);
  createUser.mockResolvedValue({ id: 'u3', username: 'nuevo@test.com', email: 'nuevo@test.com', firstName: 'Nuevo', lastName: 'Usuario', enabled: true, role: 'VIEWER' });
  setUserEnabled.mockResolvedValue();
});

describe('UsersPage', () => {
  it('muestra la lista de usuarios con su rol y estado', async () => {
    renderUsersPage();

    expect(await screen.findByText('admin@test.com')).toBeInTheDocument();
    expect(screen.getByText('viewer@test.com')).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).getByText('ADMIN')).toBeInTheDocument();
    expect(within(table).getByText('VIEWER')).toBeInTheDocument();
    expect(screen.getByText('Habilitado')).toBeInTheDocument();
    expect(screen.getByText('Deshabilitado')).toBeInTheDocument();
  });

  it('crea un usuario con datos válidos y muestra un toast de éxito', async () => {
    const user = userEvent.setup();
    renderUsersPage();

    await screen.findByText('admin@test.com');

    await user.type(screen.getByLabelText('Email'), 'nuevo@test.com');
    await user.type(screen.getByLabelText('Nombre'), 'Nuevo');
    await user.type(screen.getByLabelText('Apellido'), 'Usuario');
    await user.type(screen.getByLabelText('Contraseña temporal'), 'password123');
    await user.selectOptions(screen.getByLabelText('Rol'), 'VIEWER');
    await user.click(screen.getByRole('button', { name: 'Crear usuario' }));

    await waitFor(() => expect(createUser).toHaveBeenCalled());
    expect(createUser.mock.calls[0][0]).toEqual({
      email: 'nuevo@test.com',
      firstName: 'Nuevo',
      lastName: 'Usuario',
      password: 'password123',
      role: 'VIEWER',
    });
    expect(await screen.findByText('Usuario creado correctamente.')).toBeInTheDocument();
  });

  it('muestra errores de validación al enviar el formulario vacío', async () => {
    const user = userEvent.setup();
    renderUsersPage();

    await screen.findByText('admin@test.com');
    await user.click(screen.getByRole('button', { name: 'Crear usuario' }));

    expect(await screen.findByText('El email es obligatorio')).toBeInTheDocument();
    expect(createUser).not.toHaveBeenCalled();
  });

  it('muestra el error de la API cuando el email ya existe', async () => {
    createUser.mockRejectedValue({ response: { data: { message: 'Ya existe un usuario con el email: admin@test.com' } } });
    const user = userEvent.setup();
    renderUsersPage();

    await screen.findByText('admin@test.com');
    await user.type(screen.getByLabelText('Email'), 'admin@test.com');
    await user.type(screen.getByLabelText('Nombre'), 'Admin');
    await user.type(screen.getByLabelText('Apellido'), 'Test');
    await user.type(screen.getByLabelText('Contraseña temporal'), 'password123');
    await user.selectOptions(screen.getByLabelText('Rol'), 'ADMIN');
    await user.click(screen.getByRole('button', { name: 'Crear usuario' }));

    const alerts = await screen.findAllByRole('alert');
    expect(alerts.map((alert) => alert.textContent)).toContain('Ya existe un usuario con el email: admin@test.com');
  });

  it('deshabilitar un usuario pide confirmación antes de llamar a la API', async () => {
    const user = userEvent.setup();
    renderUsersPage();

    await screen.findByText('admin@test.com');
    await user.click(screen.getByRole('button', { name: 'Deshabilitar' }));

    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText(/¿Deshabilitar al usuario "admin@test.com"\?/)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Deshabilitar' }));

    await waitFor(() => {
      expect(setUserEnabled).toHaveBeenCalledWith('u1', false);
    });
  });

  it('cancelar el diálogo de deshabilitar no llama a la API', async () => {
    const user = userEvent.setup();
    renderUsersPage();

    await screen.findByText('admin@test.com');
    await user.click(screen.getByRole('button', { name: 'Deshabilitar' }));

    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    expect(setUserEnabled).not.toHaveBeenCalled();
  });

  it('habilitar un usuario deshabilitado llama a la API directamente, sin confirmación', async () => {
    const user = userEvent.setup();
    renderUsersPage();

    await screen.findByText('viewer@test.com');
    await user.click(screen.getByRole('button', { name: 'Habilitar' }));

    await waitFor(() => {
      expect(setUserEnabled).toHaveBeenCalledWith('u2', true);
    });
  });
});
