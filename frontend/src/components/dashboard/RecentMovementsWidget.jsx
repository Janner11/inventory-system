import styles from '../../styles/dashboard.module.css';

const TYPE_LABELS = {
  ENTRY: 'Entrada',
  EXIT: 'Salida',
  ADJUSTMENT: 'Ajuste',
};

function formatDateTime(value) {
  return new Date(value).toLocaleString('es-DO', { dateStyle: 'short', timeStyle: 'short' });
}

export default function RecentMovementsWidget({ movements }) {
  if (movements.length === 0) {
    return <p>Todavía no hay movimientos de stock registrados.</p>;
  }

  return (
    <div className={styles.tableWrapper}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>Producto</th>
            <th>Tipo</th>
            <th>Cantidad</th>
            <th>Realizado por</th>
            <th>Fecha</th>
          </tr>
        </thead>
        <tbody>
          {movements.map((movement) => (
            <tr key={movement.id}>
              <td>
                {movement.productName} <span className={styles.movementSku}>({movement.productSku})</span>
              </td>
              <td>{TYPE_LABELS[movement.type] ?? movement.type}</td>
              <td className={movement.quantity < 0 ? styles.negativeQuantity : styles.positiveQuantity}>
                {movement.quantity > 0 ? `+${movement.quantity}` : movement.quantity}
              </td>
              <td>{movement.performedBy}</td>
              <td>{formatDateTime(movement.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
