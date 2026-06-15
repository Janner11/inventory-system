import { Route, Routes } from 'react-router-dom';
import AppShell from '../components/layout/AppShell';
import ProtectedRoute from '../components/ProtectedRoute';
import DashboardPage from '../pages/DashboardPage';
import HomePage from '../pages/HomePage';
import NotFoundPage from '../pages/NotFoundPage';
import ProductsPage from '../pages/ProductsPage';
import StockPage from '../pages/StockPage';

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/products" element={<ProductsPage />} />
          <Route path="/stock" element={<StockPage />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
