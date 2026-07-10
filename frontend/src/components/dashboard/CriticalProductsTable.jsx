import { Link } from 'react-router-dom';
import styles from '../../styles/dashboard.module.css';

export default function CriticalProductsTable({ products }) {
  if (products.length === 0) {
    return <p>No hay productos en alerta de stock bajo.</p>;
  }

  return (
    <div className={styles.tableWrapper}>
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
            <tr key={product.id} className={styles.criticalRow}>
              <td>{product.sku}</td>
              <td>
                <Link to={`/products/${product.id}`}>{product.name}</Link>
              </td>
              <td>{product.quantity}</td>
              <td>{product.minStock}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
