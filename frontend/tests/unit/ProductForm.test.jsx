import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import ProductForm from '../../src/components/products/ProductForm';

const noop = () => {};

describe('ProductForm', () => {
  it('renderiza todos los campos y botones del formulario', () => {
    render(<ProductForm onSubmit={noop} onCancel={noop} isSubmitting={false} />);

    expect(screen.getByLabelText('Nombre')).toBeInTheDocument();
    expect(screen.getByLabelText('SKU')).toBeInTheDocument();
    expect(screen.getByLabelText('Descripción')).toBeInTheDocument();
    expect(screen.getByLabelText('Categoría')).toBeInTheDocument();
    expect(screen.getByLabelText('Precio')).toBeInTheDocument();
    expect(screen.getByLabelText('Cantidad')).toBeInTheDocument();
    expect(screen.getByLabelText('Stock mínimo')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeInTheDocument();
  });

  it('crea un producto con datos válidos', async () => {
    const handleSubmit = vi.fn();
    render(<ProductForm onSubmit={handleSubmit} onCancel={noop} isSubmitting={false} />);

    await userEvent.type(screen.getByLabelText('Nombre'), 'Teclado mecánico');
    await userEvent.type(screen.getByLabelText('SKU'), 'tec-010');
    await userEvent.type(screen.getByLabelText('Categoría'), 'Electronica');
    await userEvent.type(screen.getByLabelText('Precio'), '49.99');
    await userEvent.type(screen.getByLabelText('Cantidad'), '20');
    await userEvent.type(screen.getByLabelText('Stock mínimo'), '5');

    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(handleSubmit).toHaveBeenCalledTimes(1);
    expect(handleSubmit).toHaveBeenCalledWith({
      name: 'Teclado mecánico',
      sku: 'TEC-010',
      description: '',
      category: 'Electronica',
      price: 49.99,
      quantity: 20,
      minStock: 5,
    });
  });

  it('precarga los datos del producto en modo edición', () => {
    const product = {
      name: 'Mouse Logitech',
      sku: 'TEC-002',
      description: 'Mouse inalámbrico',
      category: 'Electronica',
      price: 25.5,
      quantity: 8,
      minStock: 10,
    };

    render(<ProductForm initialValues={product} onSubmit={noop} onCancel={noop} isSubmitting={false} />);

    expect(screen.getByLabelText('Nombre')).toHaveValue('Mouse Logitech');
    expect(screen.getByLabelText('SKU')).toHaveValue('TEC-002');
    expect(screen.getByLabelText('Descripción')).toHaveValue('Mouse inalámbrico');
    expect(screen.getByLabelText('Categoría')).toHaveValue('Electronica');
    expect(screen.getByLabelText('Precio')).toHaveValue(25.5);
    expect(screen.getByLabelText('Cantidad')).toHaveValue(8);
    expect(screen.getByLabelText('Stock mínimo')).toHaveValue(10);
  });

  it('muestra errores de validación cuando los datos son inválidos', async () => {
    const handleSubmit = vi.fn();
    render(<ProductForm onSubmit={handleSubmit} onCancel={noop} isSubmitting={false} />);

    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('El nombre es obligatorio')).toBeInTheDocument();
    expect(screen.getByText('El SKU es obligatorio')).toBeInTheDocument();
    expect(screen.getByText('La categoría es obligatoria')).toBeInTheDocument();
    expect(screen.getByText('El precio es obligatorio')).toBeInTheDocument();
    expect(screen.getByText('La cantidad es obligatoria')).toBeInTheDocument();
    expect(screen.getByText('El stock mínimo es obligatorio')).toBeInTheDocument();
    expect(handleSubmit).not.toHaveBeenCalled();
  });

  it('muestra un mensaje de error cuando el precio no es mayor que 0', async () => {
    render(<ProductForm onSubmit={vi.fn()} onCancel={noop} isSubmitting={false} />);

    await userEvent.type(screen.getByLabelText('Nombre'), 'Producto');
    await userEvent.type(screen.getByLabelText('SKU'), 'SKU-1');
    await userEvent.type(screen.getByLabelText('Categoría'), 'Categoria');
    await userEvent.type(screen.getByLabelText('Precio'), '0');
    await userEvent.type(screen.getByLabelText('Cantidad'), '1');
    await userEvent.type(screen.getByLabelText('Stock mínimo'), '0');

    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('El precio debe ser mayor que 0')).toBeInTheDocument();
  });

  it('muestra el mensaje de error devuelto por la API', () => {
    render(<ProductForm onSubmit={noop} onCancel={noop} isSubmitting={false} apiError="sku: ya existe un producto con ese SKU" />);

    expect(screen.getByRole('alert')).toHaveTextContent('sku: ya existe un producto con ese SKU');
  });

  it('llama a onCancel al hacer click en Cancelar', async () => {
    const handleCancel = vi.fn();
    render(<ProductForm onSubmit={noop} onCancel={handleCancel} isSubmitting={false} />);

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(handleCancel).toHaveBeenCalledTimes(1);
  });
});
