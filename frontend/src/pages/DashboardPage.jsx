import QuickAccessCard from '../components/dashboard/QuickAccessCard';
import { useAuth } from '../hooks/useAuth';
import styles from '../styles/dashboard.module.css';

const QUICK_ACCESS_ITEMS = [
  {
    to: '/products',
    title: 'Productos',
    description: 'Consulta y gestiona el catálogo de productos del inventario.',
  },
  {
    to: '/stock',
    title: 'Stock',
    description: 'Registra entradas y salidas, y consulta el historial de movimientos.',
  },
];

export default function DashboardPage() {
  const { user } = useAuth();
  const roles = user?.resource_access?.['inventario-backend']?.roles ?? [];

  return (
    <div className={styles.dashboard}>
      <h1>Dashboard</h1>
      <p className={styles.welcome}>
        Bienvenido, <strong>{user?.preferred_username}</strong>
      </p>
      {roles.length > 0 && <p className={styles.roles}>Permisos: {roles.join(', ')}</p>}

      <h2 className={styles.sectionTitle}>Accesos rápidos</h2>
      <div className={styles.quickAccessGrid}>
        {QUICK_ACCESS_ITEMS.map((item) => (
          <QuickAccessCard key={item.to} to={item.to} title={item.title} description={item.description} />
        ))}
      </div>
    </div>
  );
}
