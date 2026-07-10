import { Link } from 'react-router-dom';
import styles from '../../styles/dashboard.module.css';

export default function TopProductsWidget({ products }) {
  if (products.length === 0) {
    return <p>Todavía no hay movimientos de stock en los últimos 30 días.</p>;
  }

  return (
    <ol className={styles.topProductsList}>
      {products.map((product) => (
        <li key={product.productId} className={styles.topProductsItem}>
          <Link to={`/products/${product.productId}`}>{product.name}</Link>
          <span className={styles.movementSku}>({product.sku})</span>
          <span className={styles.topProductsCount}>{product.movementCount} movimientos</span>
        </li>
      ))}
    </ol>
  );
}
