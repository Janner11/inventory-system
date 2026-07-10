import { Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import styles from '../styles/auth.module.css';

export default function LoginPage() {
  const { isAuthenticated, isLoading, login } = useAuth();

  if (isLoading) {
    return <p>Cargando sesión...</p>;
  }

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <h1>Sistema de Gestión de Inventarios</h1>
        <p>Inicia sesión para acceder al panel de control.</p>
        <button className={styles.loginButton} onClick={login}>
          Iniciar sesión
        </button>
      </div>
    </div>
  );
}
