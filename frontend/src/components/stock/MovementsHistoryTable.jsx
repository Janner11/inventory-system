import styles from '../../styles/stock.module.css';

const TYPE_LABELS = {
  ENTRY: 'Entrada',
  EXIT: 'Salida',
  ADJUSTMENT: 'Ajuste',
};

const TYPE_BADGE_CLASS = {
  ENTRY: styles.badgeSuccess,
  EXIT: styles.badgeDanger,
  ADJUSTMENT: styles.badgeInfo,
};

function formatDate(isoString) {
  return new Date(isoString).toLocaleString('es-DO');
}

export default function MovementsHistoryTable({ movements }) {
  if (movements.length === 0) {
    return <p>No se encontraron movimientos.</p>;
  }

  return (
    <table className={styles.table}>
      <thead>
        <tr>
          <th>Fecha</th>
          <th>Producto</th>
          <th>Tipo</th>
          <th>Cantidad</th>
          <th>Stock resultante</th>
          <th>Motivo</th>
          <th>Realizado por</th>
        </tr>
      </thead>
      <tbody>
        {movements.map((movement) => (
          <tr key={movement.id}>
            <td>{formatDate(movement.createdAt)}</td>
            <td>
              {movement.productSku} — {movement.productName}
            </td>
            <td>
              <span className={TYPE_BADGE_CLASS[movement.type]}>{TYPE_LABELS[movement.type]}</span>
            </td>
            <td>{movement.quantity > 0 ? `+${movement.quantity}` : movement.quantity}</td>
            <td>
              {movement.previousQuantity} → {movement.newQuantity}
            </td>
            <td>{movement.reason || '—'}</td>
            <td>{movement.performedBy}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
