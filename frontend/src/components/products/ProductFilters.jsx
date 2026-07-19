import styles from '../../styles/products.module.css';

export default function ProductFilters({
  search,
  onSearchChange,
  category,
  onCategoryChange,
  categories,
  status,
  onStatusChange,
  minPrice,
  onMinPriceChange,
  maxPrice,
  onMaxPriceChange,
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
        <label htmlFor="product-status">Estado</label>
        <select id="product-status" value={status} onChange={(event) => onStatusChange(event.target.value)}>
          <option value="">Activos</option>
          <option value="INACTIVE">Inactivos</option>
        </select>
      </div>

      <div className={styles.filterField}>
        <label htmlFor="product-min-price">Precio mínimo</label>
        <input
          id="product-min-price"
          type="number"
          min="0"
          step="0.01"
          placeholder="0.00"
          value={minPrice}
          onChange={(event) => onMinPriceChange(event.target.value)}
        />
      </div>

      <div className={styles.filterField}>
        <label htmlFor="product-max-price">Precio máximo</label>
        <input
          id="product-max-price"
          type="number"
          min="0"
          step="0.01"
          placeholder="0.00"
          value={maxPrice}
          onChange={(event) => onMaxPriceChange(event.target.value)}
        />
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
