import { Link } from 'react-router-dom';
import styles from '../../styles/dashboard.module.css';

export default function QuickAccessCard({ to, title, description }) {
  // FRONT-008: sin aria-label propio - WCAG 2.5.3 (Label in Name) exige que el nombre
  // accesible contenga el texto visible completo. El "Ir a ${title}" anterior lo
  // reemplazaba por un texto distinto (findable como label-content-name-mismatch en
  // Lighthouse/axe-core). El nombre accesible ahora se calcula del contenido visible
  // (titulo + descripcion) sin necesitar coincidir con el enlace homonimo del Sidebar -
  // dos enlaces con el mismo nombre en regiones distintas de la pagina (nav vs main) no
  // es en si una violacion de WCAG.
  return (
    <Link to={to} className={styles.card}>
      <h3 className={styles.cardTitle}>{title}</h3>
      <p className={styles.cardDescription}>{description}</p>
    </Link>
  );
}
