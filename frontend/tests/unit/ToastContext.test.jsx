import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../src/context/ToastContext';
import { useToast } from '../../src/hooks/useToast';

function Consumer() {
  const { showToast } = useToast();
  return (
    <>
      <button type="button" onClick={() => showToast('Guardado correctamente.')}>
        Mostrar éxito
      </button>
      <button type="button" onClick={() => showToast('Algo salió mal.', { type: 'error' })}>
        Mostrar error
      </button>
      <button type="button" onClick={() => showToast('Sin auto-dismiss.', { duration: 0 })}>
        Mostrar sin auto-dismiss
      </button>
      <button type="button" onClick={() => showToast('Se autodescarta.', { duration: 1000 })}>
        Mostrar con duración corta
      </button>
    </>
  );
}

function BareConsumer() {
  useToast();
  return null;
}

function renderWithProvider() {
  return render(
    <ToastProvider>
      <Consumer />
    </ToastProvider>,
  );
}

describe('useToast', () => {
  it('lanza un error explícito cuando se usa fuera de ToastProvider', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<BareConsumer />)).toThrow('useToast debe usarse dentro de ToastProvider');
    consoleSpy.mockRestore();
  });
});

describe('ToastProvider', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('muestra un toast de éxito con role="status"', async () => {
    renderWithProvider();

    await userEvent.click(screen.getByRole('button', { name: 'Mostrar éxito' }));

    expect(screen.getByText('Guardado correctamente.').closest('[role="status"]')).toBeInTheDocument();
  });

  it('muestra un toast de error con role="alert"', async () => {
    renderWithProvider();

    await userEvent.click(screen.getByRole('button', { name: 'Mostrar error' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Algo salió mal.');
  });

  it('permite mostrar varios toasts a la vez', async () => {
    renderWithProvider();

    await userEvent.click(screen.getByRole('button', { name: 'Mostrar éxito' }));
    await userEvent.click(screen.getByRole('button', { name: 'Mostrar error' }));

    expect(await screen.findByText('Guardado correctamente.')).toBeInTheDocument();
    expect(screen.getByText('Algo salió mal.')).toBeInTheDocument();
  });

  it('se puede cerrar manualmente con el botón de cierre', async () => {
    renderWithProvider();

    await userEvent.click(screen.getByRole('button', { name: 'Mostrar sin auto-dismiss' }));
    expect(await screen.findByText('Sin auto-dismiss.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar notificación' }));

    await waitFor(() => expect(screen.queryByText('Sin auto-dismiss.')).not.toBeInTheDocument());
  });

  it('se descarta automáticamente pasada la duración indicada', () => {
    vi.useFakeTimers();
    renderWithProvider();

    fireEvent.click(screen.getByRole('button', { name: 'Mostrar con duración corta' }));
    expect(screen.getByText('Se autodescarta.')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(1000);
    });

    expect(screen.queryByText('Se autodescarta.')).not.toBeInTheDocument();
  });
});
