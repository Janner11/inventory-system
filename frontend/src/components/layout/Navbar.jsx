import { useAuth } from '../../hooks/useAuth';
import styles from '../../styles/layout.module.css';

export default function Navbar() {
  const { user, logout } = useAuth();

  return (
    <header className={styles.navbar}>
      <span className={styles.navbarBrand}>Sistema de Inventarios</span>
      <div className={styles.navbarUser}>
        <span>{user?.preferred_username}</span>
        <button type="button" className={styles.logoutButton} onClick={logout}>
          Cerrar sesión
        </button>
      </div>
    </header>
  );
}
