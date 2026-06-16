import styles from '../../styles/products.module.css';

export default function ProductFilters({
  search,
  onSearchChange,
  category,
  onCategoryChange,
  categories,
  lowStockOnly,
  onLowStockOnlyChange,
}) {
  return (
    <div className={styles.filters}>
      <div className={styles.filterField}>
        <label htmlFor="product-search">Buscar</label>
        <input
          id="product-search"
          type="search"
          placeholder="Nombre o SKU"
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
        />
      </div>

      <div className={styles.filterField}>
        <label htmlFor="product-category">Categoría</label>
        <select
          id="product-category"
          value={category}
          onChange={(event) => onCategoryChange(event.target.value)}
        >
          <option value="">Todas</option>
          {categories.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.filterField}>
        <label htmlFor="product-low-stock">
          <input
            id="product-low-stock"
            type="checkbox"
            checked={lowStockOnly}
            onChange={(event) => onLowStockOnlyChange(event.target.checked)}
          />
          Solo stock bajo
        </label>
      </div>
    </div>
  );
}
