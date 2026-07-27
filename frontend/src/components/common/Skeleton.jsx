import styles from '../../styles/skeleton.module.css';

function SkeletonCards({ count }) {
  return (
    <div className={styles.cardGrid}>
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className={styles.card} aria-hidden="true">
          <span className={`${styles.block} ${styles.cardLabel}`} />
          <span className={`${styles.block} ${styles.cardValue}`} />
        </div>
      ))}
    </div>
  );
}

function SkeletonTable({ count }) {
  return (
    <table className={styles.table} aria-hidden="true">
      <tbody>
        {Array.from({ length: count }).map((_, index) => (
          <tr key={index}>
            <td>
              <span className={styles.block} />
            </td>
            <td>
              <span className={styles.block} />
            </td>
            <td>
              <span className={styles.block} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function SkeletonText({ count }) {
  return (
    <div aria-hidden="true">
      {Array.from({ length: count }).map((_, index) => (
        <span key={index} className={`${styles.block} ${styles.textLine}`} />
      ))}
    </div>
  );
}

export default function Skeleton({ variant = 'text', count = 1, label = 'Cargando...' }) {
  return (
    <div className={styles.wrapper} role="status" aria-label={label}>
      {variant === 'card' && <SkeletonCards count={count} />}
      {variant === 'table' && <SkeletonTable count={count} />}
      {variant === 'text' && <SkeletonText count={count} />}
    </div>
  );
}
