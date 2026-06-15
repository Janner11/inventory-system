import styles from '../../styles/products.module.css';

export default function ProductsTable({ products }) {
  if (products.length === 0) {
    return <p>No se encontraron productos.</p>;
  }

  return (
    <table className={styles.table}>
      <thead>
        <tr>
          <th>SKU</th>
          <th>Nombre</th>
          <th>Categoría</th>
          <th>Precio</th>
          <th>Cantidad</th>
          <th>Stock mínimo</th>
          <th>Estado</th>
        </tr>
      </thead>
      <tbody>
        {products.map((product) => {
          const lowStock = product.quantity < product.minStock;
          return (
            <tr key={product.id} className={lowStock ? styles.lowStockRow : undefined}>
              <td>{product.sku}</td>
              <td>{product.name}</td>
              <td>{product.category}</td>
              <td>${product.price.toFixed(2)}</td>
              <td>{product.quantity}</td>
              <td>{product.minStock}</td>
              <td>
                {lowStock ? (
                  <span className={styles.badgeWarning}>Stock bajo</span>
                ) : (
                  <span className={styles.badgeSuccess}>{product.status}</span>
                )}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
