import { useState } from 'react';
import { Link } from 'react-router-dom';
import ConfirmDialog from '../common/ConfirmDialog';
import { useDeleteProduct } from '../../hooks/useProducts';
import { useToast } from '../../hooks/useToast';
import styles from '../../styles/products.module.css';

const SORTABLE_COLUMNS = [
  { field: 'sku', label: 'SKU' },
  { field: 'name', label: 'Nombre' },
  { field: 'category', label: 'Categoría' },
  { field: 'price', label: 'Precio' },
  { field: 'quantity', label: 'Cantidad' },
  { field: 'minStock', label: 'Stock mínimo' },
  { field: 'status', label: 'Estado' },
];

function SortableHeader({ field, label, sortField, sortDirection, onSortChange }) {
  const isActive = sortField === field;
  const ariaSort = isActive ? (sortDirection === 'asc' ? 'ascending' : 'descending') : 'none';
  const indicator = isActive ? (sortDirection === 'asc' ? ' ▲' : ' ▼') : '';

  return (
    <th aria-sort={ariaSort}>
      <button type="button" className={styles.sortButton} onClick={() => onSortChange(field)}>
        {label}
        {indicator}
      </button>
    </th>
  );
}

export default function ProductsTable({
  products,
  canManage = false,
  sortField,
  sortDirection,
  onSortChange = () => {},
}) {
  const deleteMutation = useDeleteProduct();
  const { showToast } = useToast();
  const [pendingDelete, setPendingDelete] = useState(null);

  function requestDelete(id, name, quantity) {
    setPendingDelete({ id, name, quantity });
  }

  function cancelDelete() {
    setPendingDelete(null);
  }

  function confirmDelete() {
    const { id } = pendingDelete;
    setPendingDelete(null);
    deleteMutation.mutate(id, {
      onSuccess: () => showToast('Producto eliminado correctamente.'),
      onError: () => showToast('No se pudo eliminar el producto.', { type: 'error' }),
    });
  }

  const confirmMessage = pendingDelete
    ? pendingDelete.quantity > 0
      ? `¿Eliminar el producto "${pendingDelete.name}"? Todavía tiene ${pendingDelete.quantity} unidades en stock.`
      : `¿Eliminar el producto "${pendingDelete.name}"?`
    : '';

  if (products.length === 0) {
    return <p>No se encontraron productos.</p>;
  }

  return (
    <>
      <table className={styles.table}>
        <thead>
          <tr>
            {SORTABLE_COLUMNS.map(({ field, label }) => (
              <SortableHeader
                key={field}
                field={field}
                label={label}
                sortField={sortField}
                sortDirection={sortDirection}
                onSortChange={onSortChange}
              />
            ))}
            <th>Acciones</th>
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
                <td>
                  <Link to={`/products/${product.id}`} className={styles.actionLink}>
                    Ver
                  </Link>
                  {canManage && (
                    <>
                      <Link to={`/products/${product.id}/edit`} className={styles.actionLink}>
                        Editar
                      </Link>
                      <button
                        type="button"
                        className={styles.deleteButton}
                        onClick={() => requestDelete(product.id, product.name, product.quantity)}
                        disabled={deleteMutation.isPending}
                      >
                        Eliminar
                      </button>
                    </>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <ConfirmDialog
        open={pendingDelete !== null}
        message={confirmMessage}
        confirmLabel="Eliminar"
        cancelLabel="Cancelar"
        onConfirm={confirmDelete}
        onCancel={cancelDelete}
      />
    </>
  );
}
