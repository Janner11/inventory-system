import { Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

export default function HomePage() {
  const { isAuthenticated, isLoading, login } = useAuth();

  if (isLoading) {
    return <p>Cargando sesión...</p>;
  }

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div>
      <h1>Sistema de Gestión de Inventarios</h1>
      <p>Inicia sesión para acceder al panel de control.</p>
      <button onClick={login}>Iniciar sesión</button>
    </div>
  );
}
