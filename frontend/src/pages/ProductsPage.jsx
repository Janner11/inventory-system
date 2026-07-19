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

export default function ProductsPage() {
  const { hasScope } = useAuth();
  const canManage = hasScope('product:manage');
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [page, setPage] = useState(1);

  const { data: categories = [] } = useProductCategories();

  useEffect(() => {
    setPage(1);
  }, [search, category, lowStockOnly]);

  // Paginación/filtro por categoría y búsqueda: resueltos server-side por GET /api/products.
  const paginatedQuery = useProducts(
    { page: page - 1, size: PAGE_SIZE, category: category || undefined, q: search || undefined },
    { enabled: !lowStockOnly },
  );

  // "Solo stock bajo" usa el endpoint dedicado GET /api/products/critical (lista completa,
  // ya que es un subconjunto pequeño); búsqueda/categoría/paginación se aplican client-side sobre ese resultado.
  const criticalQuery = useCriticalProducts({ enabled: lowStockOnly });

  const filteredCriticalProducts = useMemo(() => {
    if (!lowStockOnly || !criticalQuery.data) return [];
    const term = search.trim().toLowerCase();
    return criticalQuery.data.filter((product) => {
      const matchesSearch =
        term === '' || product.name.toLowerCase().includes(term) || product.sku.toLowerCase().includes(term);
      const matchesCategory = category === '' || product.category === category;
      return matchesSearch && matchesCategory;
    });
  }, [lowStockOnly, criticalQuery.data, search, category]);

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
            lowStockOnly={lowStockOnly}
            onLowStockOnlyChange={setLowStockOnly}
          />

          <div className={styles.tableWrapper}>
            <ProductsTable products={products} canManage={canManage} />
          </div>

          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}
