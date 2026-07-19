import styles from '../../styles/audit.module.css';

const TYPE_LABELS = {
  ADD: 'Creación',
  MOD: 'Modificación',
  DEL: 'Eliminación',
};

const TYPE_BADGES = {
  ADD: 'badgeSuccess',
  MOD: 'badgeInfo',
  DEL: 'badgeDanger',
};

function formatDateTime(value) {
  return new Date(value).toLocaleString('es-DO', { dateStyle: 'short', timeStyle: 'short' });
}

export default function RevisionsTable({ revisions }) {
  if (revisions.length === 0) {
    return <p>Este producto todavía no tiene revisiones registradas.</p>;
  }

  return (
    <div className={styles.tableWrapper}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>Revisión</th>
            <th>Tipo</th>
            <th>Fecha</th>
            <th>Realizado por</th>
            <th>Nombre</th>
            <th>Cantidad</th>
            <th>Precio</th>
            <th>Estado</th>
          </tr>
        </thead>
        <tbody>
          {revisions.map((revision) => (
            <tr key={revision.revisionNumber}>
              <td>{revision.revisionNumber}</td>
              <td>
                <span className={styles[TYPE_BADGES[revision.revisionType]] ?? styles.badgeInfo}>
                  {TYPE_LABELS[revision.revisionType] ?? revision.revisionType}
                </span>
              </td>
              <td>{formatDateTime(revision.revisionTimestamp)}</td>
              <td>{revision.revisedBy ?? <em>Desconocido</em>}</td>
              <td>{revision.name}</td>
              <td>{revision.quantity}</td>
              <td>${Number(revision.price).toFixed(2)}</td>
              <td>{revision.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
