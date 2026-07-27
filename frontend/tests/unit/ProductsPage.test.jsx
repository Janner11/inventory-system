import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../src/context/ToastContext';
import { useAuth } from '../../src/hooks/useAuth';
import ProductsPage from '../../src/pages/ProductsPage';
import { getCriticalProducts, getProducts } from '../../src/services/productService';

vi.mock('../../src/hooks/useAuth');

vi.mock('../../src/services/productService', () => ({
  getProducts: vi.fn(),
  getCriticalProducts: vi.fn(),
  deleteProduct: vi.fn(),
}));

const PAGE_SIZE = 5;

const ALL_PRODUCTS = [
  { id: '1', sku: 'TEC-001', name: 'Laptop Dell Inspiron', category: 'Electronica', price: 1200, quantity: 10, minStock: 3, status: 'ACTIVE' },
  { id: '2', sku: 'TEC-002', name: 'Mouse Logitech', category: 'Electronica', price: 25, quantity: 2, minStock: 10, status: 'ACTIVE' },
  { id: '3', sku: 'TEC-003', name: 'Teclado Mecanico', category: 'Electronica', price: 60, quantity: 15, minStock: 5, status: 'ACTIVE' },
  { id: '4', sku: 'OFI-001', name: 'Escritorio', category: 'Oficina', price: 150, quantity: 8, minStock: 2, status: 'ACTIVE' },
  { id: '5', sku: 'OFI-002', name: 'Grapadora', category: 'Oficina', price: 5, quantity: 1, minStock: 5, status: 'ACTIVE' },
  { id: '6', sku: 'ALI-001', name: 'Cafe', category: 'Alimentos', price: 8, quantity: 40, minStock: 10, status: 'ACTIVE' },
  { id: '7', sku: 'TEC-004', name: 'Monitor Descontinuado', category: 'Electronica', price: 300, quantity: 0, minStock: 2, status: 'INACTIVE' },
];

function paginate(list, page = 0, size = 20) {
  const start = page * size;
  return {
    content: list.slice(start, start + size),
    totalElements: list.length,
    totalPages: Math.max(1, Math.ceil(list.length / size)),
    number: page,
    size,
  };
}

function compareValues(a, b) {
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  return String(a).localeCompare(String(b));
}

function renderProductsPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <ProductsPage />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();

  useAuth.mockReturnValue({ hasScope: () => true });

  // Replica el comportamiento real del backend (ProductSpecifications/ProductController,
  // sección 8): sin status explícito, filtra solo ACTIVE (ADR-001).
  getProducts.mockImplementation(async ({ page = 0, size = 20, category, q, status, minPrice, maxPrice, sort } = {}) => {
    let filtered = ALL_PRODUCTS.filter((product) => product.status === (status || 'ACTIVE'));
    if (category) {
      filtered = filtered.filter((product) => product.category === category);
    }
    if (q) {
      const term = q.toLowerCase();
      filtered = filtered.filter(
        (product) => product.name.toLowerCase().includes(term) || product.sku.toLowerCase().includes(term),
      );
    }
    if (minPrice !== undefined) {
      filtered = filtered.filter((product) => product.price >= Number(minPrice));
    }
    if (maxPrice !== undefined) {
      filtered = filtered.filter((product) => product.price <= Number(maxPrice));
    }
    if (sort) {
      const [field, direction] = sort.split(',');
      filtered = [...filtered].sort((a, b) => {
        const result = compareValues(a[field], b[field]);
        return direction === 'desc' ? -result : result;
      });
    }
    return paginate(filtered, page, size);
  });

  getCriticalProducts.mockImplementation(async () =>
    ALL_PRODUCTS.filter((product) => product.status === 'ACTIVE' && product.quantity < product.minStock),
  );
});

describe('ProductsPage', () => {
  it('carga la primera página de productos server-side y muestra la paginación', async () => {
    renderProductsPage();

    expect(await screen.findByText('TEC-001')).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(PAGE_SIZE + 1); // + fila de encabezado
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument();

    expect(getProducts).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: PAGE_SIZE, category: undefined, q: undefined }),
    );
  });

  it('navega a la página siguiente pidiendo la página 2 al backend', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');

    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    // Orden alfabético por nombre (default del backend real, ProductController): Cafe,
    // Escritorio, Grapadora, Laptop Dell Inspiron y Mouse Logitech caen en la página 1;
    // Teclado Mecanico (TEC-003) queda solo en la página 2.
    expect(await screen.findByText('TEC-003')).toBeInTheDocument();
    expect(screen.getByText('Página 2 de 2')).toBeInTheDocument();
    expect(getProducts).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }));
  });

  it('filtra por texto de búsqueda (nombre o SKU) vía el backend', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');

    await user.type(screen.getByLabelText('Buscar'), 'mouse');

    await waitFor(() => {
      expect(getProducts).toHaveBeenCalledWith(expect.objectContaining({ q: 'mouse' }));
    });
    expect(await screen.findByText('Mouse Logitech')).toBeInTheDocument();
    expect(screen.queryByText('TEC-001')).not.toBeInTheDocument();
    expect(screen.getByText('Página 1 de 1')).toBeInTheDocument();
  });

  it('filtra por categoría vía el backend', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');

    await user.selectOptions(screen.getByLabelText('Categoría'), 'Oficina');

    await waitFor(() => {
      expect(getProducts).toHaveBeenCalledWith(expect.objectContaining({ category: 'Oficina' }));
    });
    expect(await screen.findByText('Escritorio')).toBeInTheDocument();
    expect(screen.getByText('Grapadora')).toBeInTheDocument();
    expect(screen.queryByText('TEC-001')).not.toBeInTheDocument();
  });

  it('filtra por rango de precio (mínimo y máximo) vía el backend', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');

    await user.type(screen.getByLabelText('Precio mínimo'), '50');
    await user.type(screen.getByLabelText('Precio máximo'), '200');

    await waitFor(() => {
      expect(getProducts).toHaveBeenCalledWith(expect.objectContaining({ minPrice: '50', maxPrice: '200' }));
    });
    // Solo Teclado Mecanico (60) y Escritorio (150) caen en [50, 200].
    expect(await screen.findByText('Teclado Mecanico')).toBeInTheDocument();
    expect(screen.getByText('Escritorio')).toBeInTheDocument();
    expect(screen.queryByText('Laptop Dell Inspiron')).not.toBeInTheDocument();
    expect(screen.getByText('Página 1 de 1')).toBeInTheDocument();
  });

  it('filtra por estado "Inactivos" vía el backend', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');

    await user.selectOptions(screen.getByLabelText('Estado'), 'INACTIVE');

    await waitFor(() => {
      expect(getProducts).toHaveBeenCalledWith(expect.objectContaining({ status: 'INACTIVE' }));
    });
    expect(await screen.findByText('Monitor Descontinuado')).toBeInTheDocument();
    expect(screen.queryByText('Laptop Dell Inspiron')).not.toBeInTheDocument();
  });

  it('ordena por precio al hacer click en el header de la columna', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');

    await user.click(screen.getByRole('button', { name: 'Precio' }));

    await waitFor(() => {
      expect(getProducts).toHaveBeenCalledWith(expect.objectContaining({ sort: 'price,asc' }));
    });
    expect(screen.getByRole('columnheader', { name: /Precio/ })).toHaveAttribute('aria-sort', 'ascending');

    const rows = await screen.findAllByRole('row');
    // La primera fila de datos (después del encabezado) debe ser el precio más bajo: Grapadora ($5).
    expect(within(rows[1]).getByText('Grapadora')).toBeInTheDocument();
  });

  it('alterna la dirección del orden al hacer click de nuevo en la misma columna', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');

    await user.click(screen.getByRole('button', { name: 'Precio' }));
    await waitFor(() => expect(getProducts).toHaveBeenCalledWith(expect.objectContaining({ sort: 'price,asc' })));

    await user.click(screen.getByRole('button', { name: /Precio/ }));
    await waitFor(() => expect(getProducts).toHaveBeenCalledWith(expect.objectContaining({ sort: 'price,desc' })));
    expect(screen.getByRole('columnheader', { name: /Precio/ })).toHaveAttribute('aria-sort', 'descending');
  });

  it('"Solo stock bajo" usa /products/critical y muestra solo productos bajo el mínimo', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');

    await user.click(screen.getByLabelText('Solo stock bajo'));

    expect(await screen.findByText('Mouse Logitech')).toBeInTheDocument();
    expect(screen.getByText('Grapadora')).toBeInTheDocument();
    expect(screen.queryByText('TEC-001')).not.toBeInTheDocument();
    expect(getCriticalProducts).toHaveBeenCalled();
    expect(screen.getAllByText('Stock bajo')).toHaveLength(2);
  });

  it('"Solo stock bajo" también respeta el filtro de precio, aplicado client-side', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');

    await user.click(screen.getByLabelText('Solo stock bajo'));
    expect(await screen.findByText('Grapadora')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Precio mínimo'), '10');

    // Grapadora ($5) queda por debajo del nuevo mínimo; Mouse Logitech ($25) sigue.
    expect(await screen.findByText('Mouse Logitech')).toBeInTheDocument();
    expect(screen.queryByText('Grapadora')).not.toBeInTheDocument();
  });

  it('vuelve a la página 1 al cambiar el término de búsqueda', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(await screen.findByText('TEC-003')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Buscar'), 'a');

    await waitFor(() => {
      expect(getProducts).toHaveBeenCalledWith(expect.objectContaining({ page: 0, q: 'a' }));
    });
  });

  it('vuelve a la página 1 al cambiar el precio mínimo', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(await screen.findByText('TEC-003')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Precio mínimo'), '1');

    await waitFor(() => {
      expect(getProducts).toHaveBeenCalledWith(expect.objectContaining({ page: 0 }));
    });
  });

  it('muestra un mensaje de error si falla la carga de productos', async () => {
    // getProducts se usa tanto para la lista paginada como para useProductCategories();
    // se rechaza siempre (no solo la primera llamada) para no depender del orden de ejecución de los hooks.
    getProducts.mockRejectedValue(new Error('Network Error'));

    renderProductsPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudieron cargar los productos');
  });

  it('muestra "No se encontraron productos" cuando el filtro no tiene resultados', async () => {
    const user = userEvent.setup();
    renderProductsPage();

    await screen.findByText('TEC-001');

    await user.type(screen.getByLabelText('Buscar'), 'inexistente-xyz');

    expect(await screen.findByText('No se encontraron productos.')).toBeInTheDocument();
  });
});

describe('ProductsPage - fila de acciones', () => {
  it('cada fila enlaza a Ver y Editar del producto', async () => {
    renderProductsPage();

    const row = await screen.findByRole('row', { name: /TEC-001/ });
    expect(within(row).getByRole('link', { name: 'Ver' })).toHaveAttribute('href', '/products/1');
    expect(within(row).getByRole('link', { name: 'Editar' })).toHaveAttribute('href', '/products/1/edit');
  });
});
