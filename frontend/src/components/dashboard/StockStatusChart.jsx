import styles from '../../styles/dashboard.module.css';

function percentage(part, total) {
  return total > 0 ? Math.round((part / total) * 100) : 0;
}

export default function StockStatusChart({ summary }) {
  const { totalProducts, activeProducts, inactiveProducts, belowMinStockProducts } = summary;

  const activePct = percentage(activeProducts, totalProducts);
  const inactivePct = percentage(inactiveProducts, totalProducts);

  const healthyStockProducts = Math.max(activeProducts - belowMinStockProducts, 0);
  const belowMinStockPct = percentage(belowMinStockProducts, activeProducts);
  const healthyStockPct = percentage(healthyStockProducts, activeProducts);

  return (
    <div className={styles.chart}>
      <div className={styles.chartRow}>
        <span className={styles.chartRowLabel}>Catálogo ({totalProducts})</span>
        <div className={styles.chartBar} role="img" aria-label={`${activePct}% de productos activos, ${inactivePct}% inactivos`}>
          <div className={styles.chartSegmentActive} style={{ width: `${activePct}%` }} />
          <div className={styles.chartSegmentInactive} style={{ width: `${inactivePct}%` }} />
        </div>
        <span className={styles.chartLegend}>
          <span className={styles.legendDotActive} /> Activos ({activeProducts})
          <span className={styles.legendDotInactive} /> Inactivos ({inactiveProducts})
        </span>
      </div>

      <div className={styles.chartRow}>
        <span className={styles.chartRowLabel}>Stock (activos)</span>
        <div
          className={styles.chartBar}
          role="img"
          aria-label={`${healthyStockPct}% con stock saludable, ${belowMinStockPct}% bajo el mínimo`}
        >
          <div className={styles.chartSegmentActive} style={{ width: `${healthyStockPct}%` }} />
          <div className={styles.chartSegmentWarning} style={{ width: `${belowMinStockPct}%` }} />
        </div>
        <span className={styles.chartLegend}>
          <span className={styles.legendDotActive} /> Saludable ({healthyStockProducts})
          <span className={styles.legendDotWarning} /> Bajo mínimo ({belowMinStockProducts})
        </span>
      </div>
    </div>
  );
}
