import styles from '../../styles/pagination.module.css';

export default function Pagination({ page, totalPages, onPageChange }) {
  return (
    <nav className={styles.pagination} aria-label="Paginación de productos">
      <button type="button" onClick={() => onPageChange(page - 1)} disabled={page <= 1}>
        Anterior
      </button>
      <span className={styles.pageInfo}>
        Página {page} de {totalPages}
      </span>
      <button type="button" onClick={() => onPageChange(page + 1)} disabled={page >= totalPages}>
        Siguiente
      </button>
    </nav>
  );
}
