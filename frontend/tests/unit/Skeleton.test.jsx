import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import Skeleton from '../../src/components/common/Skeleton';

describe('Skeleton', () => {
  it('anuncia el estado de carga vía role="status" con la etiqueta indicada', () => {
    render(<Skeleton label="Cargando productos..." />);

    expect(screen.getByRole('status', { name: 'Cargando productos...' })).toBeInTheDocument();
  });

  it('usa "Cargando..." como etiqueta por defecto', () => {
    render(<Skeleton />);

    expect(screen.getByRole('status', { name: 'Cargando...' })).toBeInTheDocument();
  });

  it('variante "card": renderiza tantas tarjetas como indica count', () => {
    const { container } = render(<Skeleton variant="card" count={4} />);

    // eslint-disable-next-line testing-library/no-node-access
    expect(container.querySelectorAll('[aria-hidden="true"]')).toHaveLength(4);
  });

  it('variante "table": renderiza una tabla con tantas filas como indica count', () => {
    render(<Skeleton variant="table" count={3} />);

    const table = screen.getByRole('status').querySelector('table');
    expect(table).toBeInTheDocument();
    expect(table.querySelectorAll('tr')).toHaveLength(3);
  });

  it('variante "text": renderiza tantos bloques como indica count', () => {
    const { container } = render(<Skeleton variant="text" count={2} />);

    // eslint-disable-next-line testing-library/no-node-access
    expect(container.querySelectorAll('span')).toHaveLength(2);
  });

  it('los bloques decorativos quedan ocultos para lectores de pantalla (aria-hidden)', () => {
    render(<Skeleton variant="table" count={1} />);

    const table = screen.getByRole('status').querySelector('table');
    expect(table).toHaveAttribute('aria-hidden', 'true');
  });
});
