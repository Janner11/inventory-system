import { NavLink } from 'react-router-dom';
import styles from '../../styles/layout.module.css';

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/products', label: 'Productos' },
];

export default function Sidebar() {
  return (
    <nav className={styles.sidebar} aria-label="Navegación principal">
      <ul className={styles.sidebarList}>
        {NAV_ITEMS.map((item) => (
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
