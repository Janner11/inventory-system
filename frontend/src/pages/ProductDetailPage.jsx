import { Link, useNavigate, useParams } from 'react-router-dom';
import { useDeleteProduct, useProduct } from '../hooks/useProducts';
import styles from '../styles/productDetail.module.css';

export default function ProductDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: product, isLoading, isError } = useProduct(id);
  const deleteMutation = useDeleteProduct();

  function handleDelete() {
    if (window.confirm(`¿Eliminar el producto "${product.name}"?`)) {
      deleteMutation.mutate(id, {
        onSuccess: () => navigate('/products'),
      });
    }
  }

  if (isLoading) return <p>Cargando producto...</p>;
  if (isError) return <p role="alert">Producto no encontrado.</p>;
  if (!product) return null;

  const lowStock = product.quantity < product.minStock;

  return (
    <div className={styles.detailPage}>
      <div className={styles.header}>
        <h1>{product.name}</h1>
        <span className={styles.sku}>{product.sku}</span>
      </div>

      <dl className={styles.fields}>
        <div className={styles.field}>
          <dt>Descripción</dt>
          <dd>{product.description || <em>Sin descripción</em>}</dd>
        </div>
        <div className={styles.field}>
          <dt>Categoría</dt>
          <dd>{product.category}</dd>
        </div>
        <div className={styles.field}>
          <dt>Precio</dt>
          <dd>${product.price.toFixed(2)}</dd>
        </div>
        <div className={styles.field}>
          <dt>Cantidad en stock</dt>
          <dd className={lowStock ? styles.lowStock : undefined}>
            {product.quantity}
            {lowStock && <span className={styles.badge}>Stock bajo</span>}
          </dd>
        </div>
        <div className={styles.field}>
          <dt>Stock mínimo</dt>
          <dd>{product.minStock}</dd>
        </div>
        <div className={styles.field}>
          <dt>Estado</dt>
          <dd>{product.status}</dd>
        </div>
      </dl>

      <div className={styles.actions}>
        <Link to="/products" className={styles.backLink}>
          ← Volver a productos
        </Link>
        <div className={styles.actionButtons}>
          <Link to={`/products/${id}/edit`} className={styles.editButton}>
            Editar
          </Link>
          <button
            type="button"
            className={styles.deleteButton}
            onClick={handleDelete}
            disabled={deleteMutation.isPending}
          >
            Eliminar
          </button>
        </div>
      </div>
    </div>
  );
}
