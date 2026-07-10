import styles from '../../styles/dashboard.module.css';

export default function KpiCard({ label, value, variant }) {
  const valueClassName = variant === 'warning' ? `${styles.kpiValue} ${styles.kpiValueWarning}` : styles.kpiValue;

  return (
    <div className={styles.kpiCard}>
      <span className={valueClassName}>{value}</span>
      <span className={styles.kpiLabel}>{label}</span>
    </div>
  );
}
