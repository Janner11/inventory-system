import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import Pagination from '../components/common/Pagination';
import Skeleton from '../components/common/Skeleton';
import ProductFilters from '../components/products/ProductFilters';
import ProductsTable from '../components/products/ProductsTable';
import { useAuth } from '../hooks/useAuth';
import { useCriticalProducts, useProductCategories, useProducts } from '../hooks/useProducts';
import styles from '../styles/products.module.css';

const PAGE_SIZE = 5;

function compareValues(a, b) {
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  return String(a).localeCompare(String(b));
}

export default function ProductsPage() {
  const { hasScope } = useAuth();
  const canManage = hasScope('product:manage');
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [status, setStatus] = useState('');
  const [minPrice, setMinPrice] = useState('');
  const [maxPrice, setMaxPrice] = useState('');
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [sortField, setSortField] = useState('name');
  const [sortDirection, setSortDirection] = useState('asc');
  const [page, setPage] = useState(1);

  const { data: categories = [] } = useProductCategories();

  useEffect(() => {
    setPage(1);
  }, [search, category, status, minPrice, maxPrice, lowStockOnly, sortField, sortDirection]);

  function handleSortChange(field) {
    if (field === sortField) {
      setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDirection('asc');
    }
  }

  // Paginación/filtro/orden: resueltos server-side por GET /api/products.
  const paginatedQuery = useProducts(
    {
      page: page - 1,
      size: PAGE_SIZE,
      category: category || undefined,
      q: search || undefined,
      status: status || undefined,
      minPrice: minPrice || undefined,
      maxPrice: maxPrice || undefined,
      sort: `${sortField},${sortDirection}`,
    },
    { enabled: !lowStockOnly },
  );

  // "Solo stock bajo" usa el endpoint dedicado GET /api/products/critical (lista completa,
  // siempre ACTIVE — sin parámetros propios); búsqueda/categoría/status/precio/orden/paginación
  // se aplican client-side sobre ese resultado.
  const criticalQuery = useCriticalProducts({ enabled: lowStockOnly });

  const filteredCriticalProducts = useMemo(() => {
    if (!lowStockOnly || !criticalQuery.data) return [];
    const term = search.trim().toLowerCase();
    const min = minPrice === '' ? null : Number(minPrice);
    const max = maxPrice === '' ? null : Number(maxPrice);
    const filtered = criticalQuery.data.filter((product) => {
      const matchesSearch =
        term === '' || product.name.toLowerCase().includes(term) || product.sku.toLowerCase().includes(term);
      const matchesCategory = category === '' || product.category === category;
      // /products/critical solo devuelve productos ACTIVE (ProductService.getProductsBelowMinStock) —
      // filtrar por "Inactivos" aquí siempre da 0 resultados, correctamente.
      const matchesStatus = status === '' || product.status === status;
      const matchesMinPrice = min === null || product.price >= min;
      const matchesMaxPrice = max === null || product.price <= max;
      return matchesSearch && matchesCategory && matchesStatus && matchesMinPrice && matchesMaxPrice;
    });
    return [...filtered].sort((a, b) => {
      const result = compareValues(a[sortField], b[sortField]);
      return sortDirection === 'asc' ? result : -result;
    });
  }, [lowStockOnly, criticalQuery.data, search, category, status, minPrice, maxPrice, sortField, sortDirection]);

  const totalPagesForCritical = Math.max(1, Math.ceil(filteredCriticalProducts.length / PAGE_SIZE));

  useEffect(() => {
    if (lowStockOnly) {
      setPage((current) => Math.min(current, totalPagesForCritical));
    }
  }, [lowStockOnly, totalPagesForCritical]);

  const isLoading = lowStockOnly ? criticalQuery.isLoading : paginatedQuery.isLoading;
  const isError = lowStockOnly ? criticalQuery.isError : paginatedQuery.isError;
  const error = lowStockOnly ? criticalQuery.error : paginatedQuery.error;

  const products = lowStockOnly
    ? filteredCriticalProducts.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)
    : (paginatedQuery.data?.content ?? []);

  const totalPages = lowStockOnly ? totalPagesForCritical : Math.max(1, paginatedQuery.data?.totalPages ?? 1);

  return (
    <div className={styles.productsPage}>
      <div className={styles.pageHeader}>
        <h1>Productos</h1>
        {canManage && (
          <Link to="/products/new" className={styles.newProductButton}>
            Nuevo producto
          </Link>
        )}
      </div>

      {isLoading && <Skeleton variant="table" count={PAGE_SIZE} label="Cargando productos..." />}
      {isError && <p role="alert">No se pudieron cargar los productos: {error.message}</p>}

      {!isLoading && !isError && (
        <>
          <ProductFilters
            search={search}
            onSearchChange={setSearch}
            category={category}
            onCategoryChange={setCategory}
            categories={categories}
            status={status}
            onStatusChange={setStatus}
            minPrice={minPrice}
            onMinPriceChange={setMinPrice}
            maxPrice={maxPrice}
            onMaxPriceChange={setMaxPrice}
            lowStockOnly={lowStockOnly}
            onLowStockOnlyChange={setLowStockOnly}
          />

          <div className={styles.tableWrapper}>
            <ProductsTable
              products={products}
              canManage={canManage}
              sortField={sortField}
              sortDirection={sortDirection}
              onSortChange={handleSortChange}
            />
          </div>

          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}
