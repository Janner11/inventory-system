import styles from '../../styles/toast.module.css';

export default function Toast({ message, type = 'success', onDismiss }) {
  return (
    <div className={`${styles.toast} ${styles[type] ?? ''}`} role={type === 'error' ? 'alert' : 'status'}>
      <span>{message}</span>
      <button type="button" className={styles.dismissButton} onClick={onDismiss} aria-label="Cerrar notificación">
        ×
      </button>
    </div>
  );
}
