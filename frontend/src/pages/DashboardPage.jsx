import CriticalProductsTable from '../components/dashboard/CriticalProductsTable';
import KpiCard from '../components/dashboard/KpiCard';
import QuickAccessCard from '../components/dashboard/QuickAccessCard';
import RecentMovementsWidget from '../components/dashboard/RecentMovementsWidget';
import StockStatusChart from '../components/dashboard/StockStatusChart';
import TopProductsWidget from '../components/dashboard/TopProductsWidget';
import Skeleton from '../components/common/Skeleton';
import {
  useDashboardCriticalProducts,
  useDashboardRecentMovements,
  useDashboardSummary,
  useDashboardTopProducts,
} from '../hooks/useDashboard';
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

const CURRENCY_FORMATTER = new Intl.NumberFormat('es-DO', { style: 'currency', currency: 'USD' });

export default function DashboardPage() {
  const { user } = useAuth();
  const roles = user?.resource_access?.['inventario-backend']?.roles ?? [];

  const summaryQuery = useDashboardSummary();
  const criticalProductsQuery = useDashboardCriticalProducts();
  const recentMovementsQuery = useDashboardRecentMovements();
  const topProductsQuery = useDashboardTopProducts();

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

      <h2 className={styles.sectionTitle}>Resumen del inventario</h2>
      {summaryQuery.isLoading && <Skeleton variant="card" count={6} label="Cargando resumen..." />}
      {summaryQuery.isError && (
        <p role="alert">No se pudo cargar el resumen del inventario. Verifica tus permisos.</p>
      )}
      {summaryQuery.data && (
        <>
          <div className={styles.kpiGrid}>
            <KpiCard label="Productos totales" value={summaryQuery.data.totalProducts} />
            <KpiCard label="Productos activos" value={summaryQuery.data.activeProducts} />
            <KpiCard label="Productos inactivos" value={summaryQuery.data.inactiveProducts} />
            <KpiCard
              label="Bajo stock mínimo"
              value={summaryQuery.data.belowMinStockProducts}
              variant={summaryQuery.data.belowMinStockProducts > 0 ? 'warning' : undefined}
            />
            <KpiCard
              label="Valor del inventario"
              value={CURRENCY_FORMATTER.format(summaryQuery.data.totalInventoryValue)}
            />
            <KpiCard label="Movimientos de stock" value={summaryQuery.data.totalStockMovements} />
          </div>
          <StockStatusChart summary={summaryQuery.data} />
        </>
      )}

      <div className={styles.widgetsGrid}>
        <section>
          <h2 className={styles.sectionTitle}>Productos en alerta</h2>
          {criticalProductsQuery.isLoading && (
            <Skeleton variant="table" count={3} label="Cargando productos en alerta..." />
          )}
          {criticalProductsQuery.isError && <p role="alert">No se pudieron cargar los productos en alerta.</p>}
          {criticalProductsQuery.data && <CriticalProductsTable products={criticalProductsQuery.data} />}
        </section>

        <section>
          <h2 className={styles.sectionTitle}>Movimientos recientes</h2>
          {recentMovementsQuery.isLoading && (
            <Skeleton variant="table" count={3} label="Cargando movimientos recientes..." />
          )}
          {recentMovementsQuery.isError && <p role="alert">No se pudieron cargar los movimientos recientes.</p>}
          {recentMovementsQuery.data && <RecentMovementsWidget movements={recentMovementsQuery.data} />}
        </section>
      </div>

      <h2 className={styles.sectionTitle}>Más movidos (últimos 30 días)</h2>
      {topProductsQuery.isLoading && <Skeleton variant="table" count={3} label="Cargando productos más movidos..." />}
      {topProductsQuery.isError && <p role="alert">No se pudo cargar el ranking de productos.</p>}
      {topProductsQuery.data && <TopProductsWidget products={topProductsQuery.data} />}
    </div>
  );
}
