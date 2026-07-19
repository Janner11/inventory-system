import { useSearchParams } from 'react-router-dom';
import RevisionsTable from '../components/audit/RevisionsTable';
import { useProductRevisions } from '../hooks/useAudit';
import { useProducts } from '../hooks/useProducts';
import styles from '../styles/audit.module.css';

export default function AuditPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const productId = searchParams.get('productId') ?? '';

  const { data: productsPage } = useProducts({ size: 100 });
  const products = productsPage?.content ?? [];

  const revisionsQuery = useProductRevisions(productId);

  function handleProductChange(event) {
    const value = event.target.value;
    setSearchParams(value ? { productId: value } : {});
  }

  return (
    <div className={styles.auditPage}>
      <h1>Auditoría</h1>
      <p>Consulta el historial de revisiones (Hibernate Envers) de cualquier producto.</p>

      <div className={styles.selectorField}>
        <label htmlFor="audit-product">Producto</label>
        <select id="audit-product" value={productId} onChange={handleProductChange}>
          <option value="">Selecciona un producto</option>
          {products.map((product) => (
            <option key={product.id} value={product.id}>
              {product.sku} — {product.name}
            </option>
          ))}
        </select>
      </div>

      {!productId && <p>Selecciona un producto para ver su historial de revisiones.</p>}
      {productId && revisionsQuery.isLoading && <p>Cargando historial de revisiones...</p>}
      {productId && revisionsQuery.isError && (
        <p role="alert">No se pudo cargar el historial de revisiones. Verifica tus permisos.</p>
      )}
      {productId && revisionsQuery.data && <RevisionsTable revisions={revisionsQuery.data} />}
    </div>
  );
}
