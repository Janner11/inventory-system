import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import Pagination from '../components/common/Pagination';
import ProductFilters from '../components/products/ProductFilters';
import ProductsTable from '../components/products/ProductsTable';
import { useProducts } from '../hooks/useProducts';
import styles from '../styles/products.module.css';

const PAGE_SIZE = 5;

export default function ProductsPage() {
  const { data: products, isLoading, isError, error } = useProducts();

  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [page, setPage] = useState(1);

  const categories = useMemo(() => {
    if (!products) return [];
    return [...new Set(products.map((product) => product.category))].sort();
  }, [products]);

  const filteredProducts = useMemo(() => {
    if (!products) return [];
    const term = search.trim().toLowerCase();
    return products.filter((product) => {
      const matchesSearch =
        term === '' ||
        product.name.toLowerCase().includes(term) ||
        product.sku.toLowerCase().includes(term);
      const matchesCategory = category === '' || product.category === category;
      const matchesLowStock = !lowStockOnly || product.quantity < product.minStock;
      return matchesSearch && matchesCategory && matchesLowStock;
    });
  }, [products, search, category, lowStockOnly]);

  const totalPages = Math.max(1, Math.ceil(filteredProducts.length / PAGE_SIZE));

  useEffect(() => {
    setPage(1);
  }, [search, category, lowStockOnly]);

  useEffect(() => {
    setPage((current) => Math.min(current, totalPages));
  }, [totalPages]);

  const paginatedProducts = filteredProducts.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <div className={styles.productsPage}>
      <div className={styles.pageHeader}>
        <h1>Productos</h1>
        <Link to="/products/new" className={styles.newProductButton}>
          Nuevo producto
        </Link>
      </div>

      {isLoading && <p>Cargando productos...</p>}
      {isError && <p role="alert">No se pudieron cargar los productos: {error.message}</p>}

      {products && (
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
            <ProductsTable products={paginatedProducts} />
          </div>

          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}
