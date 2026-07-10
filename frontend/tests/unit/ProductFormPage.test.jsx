import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductFormPage from '../../src/pages/ProductFormPage';
import { createProduct, getProductById, updateProduct } from '../../src/services/productService';

vi.mock('../../src/services/productService', () => ({
  getProductById: vi.fn(),
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  getProducts: vi.fn(),
  getCriticalProducts: vi.fn(),
  deleteProduct: vi.fn(),
}));

const EXISTING_PRODUCT = {
  id: 'abc-123',
  name: 'Mouse Logitech',
  sku: 'TEC-002',
  description: 'Mouse inalámbrico',
  category: 'Electronica',
  price: 25.5,
  quantity: 8,
  minStock: 10,
};

function renderPage(initialPath) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/products" element={<p>Página de Productos</p>} />
          <Route path="/products/new" element={<ProductFormPage />} />
          <Route path="/products/:id/edit" element={<ProductFormPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ProductFormPage - modo creación', () => {
  it('renderiza "Nuevo producto" con el formulario vacío y no consulta el backend', async () => {
    renderPage('/products/new');

    expect(screen.getByRole('heading', { name: 'Nuevo producto' })).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre')).toHaveValue('');
    expect(getProductById).not.toHaveBeenCalled();
  });

  it('al guardar, crea el producto y navega a /products', async () => {
    const user = userEvent.setup();
    createProduct.mockResolvedValue({ id: 'new-id', sku: 'TEC-010' });

    renderPage('/products/new');

    await user.type(screen.getByLabelText('Nombre'), 'Teclado mecánico');
    await user.type(screen.getByLabelText('SKU'), 'tec-010');
    await user.type(screen.getByLabelText('Categoría'), 'Electronica');
    await user.type(screen.getByLabelText('Precio'), '49.99');
    await user.type(screen.getByLabelText('Cantidad'), '20');
    await user.type(screen.getByLabelText('Stock mínimo'), '5');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(createProduct).toHaveBeenCalled());
    expect(createProduct.mock.calls[0][0]).toEqual(expect.objectContaining({ sku: 'TEC-010' }));
    expect(await screen.findByText('Página de Productos')).toBeInTheDocument();
  });

  it('muestra el error de SKU duplicado (409) devuelto por la API sin navegar', async () => {
    const user = userEvent.setup();
    createProduct.mockRejectedValue({
      response: { status: 409, data: { message: 'Ya existe un producto con el SKU: TEC-010' } },
    });

    renderPage('/products/new');

    await user.type(screen.getByLabelText('Nombre'), 'Teclado mecánico');
    await user.type(screen.getByLabelText('SKU'), 'TEC-010');
    await user.type(screen.getByLabelText('Categoría'), 'Electronica');
    await user.type(screen.getByLabelText('Precio'), '49.99');
    await user.type(screen.getByLabelText('Cantidad'), '20');
    await user.type(screen.getByLabelText('Stock mínimo'), '5');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya existe un producto con el SKU: TEC-010');
    expect(screen.getByRole('heading', { name: 'Nuevo producto' })).toBeInTheDocument();
  });

  it('muestra un mensaje genérico cuando el error de la API no trae response (falla de red)', async () => {
    const user = userEvent.setup();
    createProduct.mockRejectedValue(new Error('Network Error'));

    renderPage('/products/new');

    await user.type(screen.getByLabelText('Nombre'), 'Producto');
    await user.type(screen.getByLabelText('SKU'), 'SKU-1');
    await user.type(screen.getByLabelText('Categoría'), 'Categoria');
    await user.type(screen.getByLabelText('Precio'), '10');
    await user.type(screen.getByLabelText('Cantidad'), '1');
    await user.type(screen.getByLabelText('Stock mínimo'), '0');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ocurrió un error inesperado. Intenta nuevamente.');
  });

  it('"Cancelar" navega a /products sin llamar a la API', async () => {
    const user = userEvent.setup();
    renderPage('/products/new');

    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(await screen.findByText('Página de Productos')).toBeInTheDocument();
    expect(createProduct).not.toHaveBeenCalled();
  });
});

describe('ProductFormPage - modo edición', () => {
  it('muestra "Cargando producto..." y luego precarga el formulario con los datos existentes', async () => {
    getProductById.mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve(EXISTING_PRODUCT), 10)),
    );

    renderPage('/products/abc-123/edit');

    expect(screen.getByText('Cargando producto...')).toBeInTheDocument();

    expect(await screen.findByRole('heading', { name: 'Editar producto' })).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre')).toHaveValue('Mouse Logitech');
    expect(screen.getByLabelText('SKU')).toHaveValue('TEC-002');
    expect(getProductById).toHaveBeenCalledWith('abc-123');
  });

  it('muestra un mensaje de error si el producto no existe (404) en vez del formulario', async () => {
    getProductById.mockRejectedValue({ response: { status: 404 } });

    renderPage('/products/no-existe/edit');

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo cargar el producto solicitado.');
    expect(screen.queryByRole('heading', { name: 'Editar producto' })).not.toBeInTheDocument();
  });

  it('al guardar, actualiza el producto existente y navega a /products', async () => {
    const user = userEvent.setup();
    getProductById.mockResolvedValue(EXISTING_PRODUCT);
    updateProduct.mockResolvedValue({ ...EXISTING_PRODUCT, quantity: 3 });

    renderPage('/products/abc-123/edit');

    await screen.findByRole('heading', { name: 'Editar producto' });

    const quantityInput = screen.getByLabelText('Cantidad');
    await user.clear(quantityInput);
    await user.type(quantityInput, '3');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() =>
      expect(updateProduct).toHaveBeenCalledWith('abc-123', expect.objectContaining({ quantity: 3 })),
    );
    expect(await screen.findByText('Página de Productos')).toBeInTheDocument();
  });
});
