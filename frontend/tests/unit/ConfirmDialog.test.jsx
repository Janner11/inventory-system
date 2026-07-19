import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import ConfirmDialog from '../../src/components/common/ConfirmDialog';

describe('ConfirmDialog', () => {
  it('no renderiza nada cuando open es false', () => {
    render(<ConfirmDialog open={false} message="¿Confirmar?" onConfirm={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('renderiza el mensaje y los botones cuando open es true', () => {
    render(<ConfirmDialog open message="¿Eliminar el producto X?" onConfirm={vi.fn()} onCancel={vi.fn()} />);

    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByText('¿Eliminar el producto X?')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Eliminar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeInTheDocument();
  });

  it('acepta labels custom para los botones', () => {
    render(
      <ConfirmDialog
        open
        message="¿Continuar?"
        confirmLabel="Sí, continuar"
        cancelLabel="No, volver"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Sí, continuar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'No, volver' })).toBeInTheDocument();
  });

  it('invoca onConfirm al hacer click en el botón de confirmar', async () => {
    const onConfirm = vi.fn();
    render(<ConfirmDialog open message="¿Confirmar?" onConfirm={onConfirm} onCancel={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('invoca onCancel al hacer click en el botón de cancelar', async () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog open message="¿Confirmar?" onConfirm={vi.fn()} onCancel={onCancel} />);

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('invoca onCancel al hacer click fuera del diálogo (overlay)', async () => {
    const onCancel = vi.fn();
    const { container } = render(<ConfirmDialog open message="¿Confirmar?" onConfirm={vi.fn()} onCancel={onCancel} />);

    // eslint-disable-next-line testing-library/no-node-access
    await userEvent.click(container.firstChild);

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('invoca onCancel al presionar Escape', async () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog open message="¿Confirmar?" onConfirm={vi.fn()} onCancel={onCancel} />);

    await userEvent.keyboard('{Escape}');

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('mueve el foco al botón de confirmar al abrirse', () => {
    render(<ConfirmDialog open message="¿Confirmar?" onConfirm={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Eliminar' })).toHaveFocus();
  });

  it('atrapa el foco dentro del diálogo: Tab desde el último elemento vuelve al primero', async () => {
    render(<ConfirmDialog open message="¿Confirmar?" onConfirm={vi.fn()} onCancel={vi.fn()} />);

    const cancelButton = screen.getByRole('button', { name: 'Cancelar' });
    const confirmButton = screen.getByRole('button', { name: 'Eliminar' });
    expect(confirmButton).toHaveFocus();

    await userEvent.tab();
    expect(cancelButton).toHaveFocus();

    await userEvent.tab({ shift: true });
    expect(confirmButton).toHaveFocus();
  });
});
