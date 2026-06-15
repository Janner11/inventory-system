import { Link } from 'react-router-dom';
import styles from '../../styles/dashboard.module.css';

export default function QuickAccessCard({ to, title, description }) {
  return (
    <Link to={to} className={styles.card} aria-label={`Ir a ${title}`}>
      <h3 className={styles.cardTitle}>{title}</h3>
      <p className={styles.cardDescription}>{description}</p>
    </Link>
  );
}
