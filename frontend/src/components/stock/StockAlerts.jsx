import styles from '../../styles/stock.module.css';

export default function StockAlerts({ products }) {
  if (products.length === 0) {
    return <p>No hay productos en alerta de stock bajo.</p>;
  }

  return (
    <table className={styles.table}>
      <thead>
        <tr>
          <th>SKU</th>
          <th>Nombre</th>
          <th>Cantidad</th>
          <th>Stock mínimo</th>
        </tr>
      </thead>
      <tbody>
        {products.map((product) => (
          <tr key={product.id} className={styles.alertRow}>
            <td>{product.sku}</td>
            <td>{product.name}</td>
            <td>{product.quantity}</td>
            <td>{product.minStock}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
