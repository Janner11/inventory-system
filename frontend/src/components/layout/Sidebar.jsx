import { NavLink } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import styles from '../../styles/layout.module.css';

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/products', label: 'Productos' },
  { to: '/stock', label: 'Stock' },
  { to: '/audit', label: 'Auditoría' },
  { to: '/reports', label: 'Reportes' },
];

// SEC-007: a diferencia del resto de enlaces (visibles para cualquier rol autenticado, el
// backend ya rechaza con 403 si falta el scope), "Usuarios" se oculta directamente — solo
// ADMIN tiene user:manage (sección 6), y un enlace visible que siempre falla no aporta nada.
const USERS_NAV_ITEM = { to: '/users', label: 'Usuarios' };

export default function Sidebar() {
  const { hasScope } = useAuth();
  const items = hasScope('user:manage') ? [...NAV_ITEMS, USERS_NAV_ITEM] : NAV_ITEMS;

  return (
    <nav className={styles.sidebar} aria-label="Navegación principal">
      <ul className={styles.sidebarList}>
        {items.map((item) => (
          <li key={item.to}>
            <NavLink to={item.to} className={styles.sidebarLink}>
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
