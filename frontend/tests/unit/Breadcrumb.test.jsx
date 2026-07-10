import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import Breadcrumb from '../../src/components/layout/Breadcrumb';

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Breadcrumb />
    </MemoryRouter>,
  );
}

describe('Breadcrumb', () => {
  it('muestra un solo nivel para /dashboard', () => {
    renderAt('/dashboard');

    expect(screen.getByText('Dashboard')).toHaveAttribute('aria-current', 'page');
  });

  it('muestra un solo nivel para /products', () => {
    renderAt('/products');

    expect(screen.getByText('Productos')).toHaveAttribute('aria-current', 'page');
  });

  it('muestra Productos > Nuevo producto en /products/new, con Productos como enlace', () => {
    renderAt('/products/new');

    expect(screen.getByRole('link', { name: 'Productos' })).toHaveAttribute('href', '/products');
    expect(screen.getByText('Nuevo producto')).toHaveAttribute('aria-current', 'page');
  });

  it('muestra Productos > Detalle en /products/:id', () => {
    renderAt('/products/123e4567-e89b-12d3-a456-426614174000');

    expect(screen.getByRole('link', { name: 'Productos' })).toHaveAttribute('href', '/products');
    expect(screen.getByText('Detalle')).toHaveAttribute('aria-current', 'page');
  });

  it('muestra Productos > Editar producto en /products/:id/edit', () => {
    renderAt('/products/123e4567-e89b-12d3-a456-426614174000/edit');

    expect(screen.getByRole('link', { name: 'Productos' })).toHaveAttribute('href', '/products');
    expect(screen.getByText('Editar producto')).toHaveAttribute('aria-current', 'page');
  });

  it('no renderiza nada para una ruta sin breadcrumb configurado', () => {
    const { container } = renderAt('/otra-ruta-desconocida');

    expect(container).toBeEmptyDOMElement();
  });
});
