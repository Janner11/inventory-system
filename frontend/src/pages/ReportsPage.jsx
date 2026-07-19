import CriticalProductsTable from '../components/dashboard/CriticalProductsTable';
import KpiCard from '../components/dashboard/KpiCard';
import RecentMovementsWidget from '../components/dashboard/RecentMovementsWidget';
import StockStatusChart from '../components/dashboard/StockStatusChart';
import TopProductsWidget from '../components/dashboard/TopProductsWidget';
import { useInventoryReport } from '../hooks/useReport';
import styles from '../styles/dashboard.module.css';

const CURRENCY_FORMATTER = new Intl.NumberFormat('es-DO', { style: 'currency', currency: 'USD' });

export default function ReportsPage() {
  const reportQuery = useInventoryReport();

  return (
    <div className={styles.dashboard}>
      <h1>Reportes</h1>
      <p>Reporte consolidado del inventario: resumen, productos críticos, movimientos recientes y más movidos.</p>

      {reportQuery.isLoading && <p>Cargando reporte...</p>}
      {reportQuery.isError && <p role="alert">No se pudo cargar el reporte. Verifica tus permisos.</p>}

      {reportQuery.data && (
        <>
          <p className={styles.roles}>
            Generado el {new Date(reportQuery.data.generatedAt).toLocaleString('es-DO', { dateStyle: 'short', timeStyle: 'short' })}
          </p>

          <h2 className={styles.sectionTitle}>Resumen del inventario</h2>
          <div className={styles.kpiGrid}>
            <KpiCard label="Productos totales" value={reportQuery.data.summary.totalProducts} />
            <KpiCard label="Productos activos" value={reportQuery.data.summary.activeProducts} />
            <KpiCard label="Productos inactivos" value={reportQuery.data.summary.inactiveProducts} />
            <KpiCard
              label="Bajo stock mínimo"
              value={reportQuery.data.summary.belowMinStockProducts}
              variant={reportQuery.data.summary.belowMinStockProducts > 0 ? 'warning' : undefined}
            />
            <KpiCard
              label="Valor del inventario"
              value={CURRENCY_FORMATTER.format(reportQuery.data.summary.totalInventoryValue)}
            />
            <KpiCard label="Movimientos de stock" value={reportQuery.data.summary.totalStockMovements} />
          </div>
          <StockStatusChart summary={reportQuery.data.summary} />

          <div className={styles.widgetsGrid}>
            <section>
              <h2 className={styles.sectionTitle}>Productos en alerta</h2>
              <CriticalProductsTable products={reportQuery.data.criticalProducts} />
            </section>

            <section>
              <h2 className={styles.sectionTitle}>Movimientos recientes</h2>
              <RecentMovementsWidget movements={reportQuery.data.recentMovements} />
            </section>
          </div>

          <h2 className={styles.sectionTitle}>Más movidos (últimos 30 días)</h2>
          <TopProductsWidget products={reportQuery.data.topProducts} />
        </>
      )}
    </div>
  );
}
