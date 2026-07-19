import { Link, matchPath, useLocation } from 'react-router-dom';
import styles from '../../styles/layout.module.css';

const ROUTES = [
  { pattern: '/dashboard', crumbs: [{ label: 'Dashboard' }] },
  { pattern: '/products/new', crumbs: [{ label: 'Productos', to: '/products' }, { label: 'Nuevo producto' }] },
  { pattern: '/products/:id/edit', crumbs: [{ label: 'Productos', to: '/products' }, { label: 'Editar producto' }] },
  { pattern: '/products/:id', crumbs: [{ label: 'Productos', to: '/products' }, { label: 'Detalle' }] },
  { pattern: '/products', crumbs: [{ label: 'Productos' }] },
  { pattern: '/stock', crumbs: [{ label: 'Stock' }] },
  { pattern: '/audit', crumbs: [{ label: 'Auditoría' }] },
  { pattern: '/reports', crumbs: [{ label: 'Reportes' }] },
];

function resolveCrumbs(pathname) {
  const route = ROUTES.find((candidate) => matchPath({ path: candidate.pattern, end: true }, pathname));
  return route?.crumbs ?? [];
}

export default function Breadcrumb() {
  const location = useLocation();
  const crumbs = resolveCrumbs(location.pathname);

  if (crumbs.length === 0) {
    return null;
  }

  return (
    <nav aria-label="Ruta de navegación" className={styles.breadcrumb}>
      <ol className={styles.breadcrumbList}>
        {crumbs.map((crumb, index) => {
          const isLast = index === crumbs.length - 1;

          return (
            <li key={crumb.label} className={styles.breadcrumbItem}>
              {isLast || !crumb.to ? (
                <span aria-current={isLast ? 'page' : undefined}>{crumb.label}</span>
              ) : (
                <Link to={crumb.to}>{crumb.label}</Link>
              )}
              {!isLast && (
                <span className={styles.breadcrumbSeparator} aria-hidden="true">
                  /
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
