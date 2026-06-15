import { useEffect, useState } from 'react';
import api from '../services/axiosConfig';

export default function DashboardPage() {
  const [products, setProducts] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api
      .get('/products')
      .then((response) => setProducts(response.data))
      .catch((err) => setError(err.response?.status ?? 'network-error'));
  }, []);

  return (
    <div>
      <h1>Dashboard</h1>

      <h2>Productos (endpoint protegido)</h2>
      {error && <p>Error al consultar /api/products: {error}</p>}
      {products && (
        <ul>
          {products.map((product) => (
            <li key={product.id}>
              {product.name} ({product.sku}) — {product.quantity} unidades
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
