import { useState } from 'react';
import Pagination from '../components/common/Pagination';
import Skeleton from '../components/common/Skeleton';
import MovementsHistoryTable from '../components/stock/MovementsHistoryTable';
import StockAlerts from '../components/stock/StockAlerts';
import StockMovementForm from '../components/stock/StockMovementForm';
import { useAuth } from '../hooks/useAuth';
import { useAdjustStock, useRegisterEntry, useRegisterExit, useStockAlerts, useStockMovements } from '../hooks/useStock';
import { useToast } from '../hooks/useToast';
import styles from '../styles/stock.module.css';

const PAGE_SIZE = 5;

function extractErrorMessage(error) {
  return error?.response?.data?.message || 'Ocurrió un error inesperado. Intenta nuevamente.';
}

export default function StockPage() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [page, setPage] = useState(1);
  const [apiError, setApiError] = useState(null);
  const [formKey, setFormKey] = useState(0);

  const alertsQuery = useStockAlerts();
  const movementsQuery = useStockMovements({ page: page - 1, size: PAGE_SIZE });

  const entryMutation = useRegisterEntry();
  const exitMutation = useRegisterExit();
  const adjustMutation = useAdjustStock();

  const isSubmitting = entryMutation.isPending || exitMutation.isPending || adjustMutation.isPending;

  function handleMovementSubmit(values) {
    setApiError(null);
    const performedBy = user?.preferred_username ?? 'desconocido';
    const callbacks = {
      onSuccess: () => {
        setPage(1);
        setFormKey((key) => key + 1);
        showToast('Movimiento registrado correctamente.');
      },
      onError: (error) => {
        const message = extractErrorMessage(error);
        setApiError(message);
        showToast(message, { type: 'error' });
      },
    };

    if (values.type === 'ADJUSTMENT') {
      adjustMutation.mutate(
        {
          productId: values.productId,
          newQuantity: values.newQuantity,
          reason: values.reason,
          observations: values.observations,
          performedBy,
        },
        callbacks,
      );
    } else if (values.type === 'EXIT') {
      exitMutation.mutate(
        { productId: values.productId, quantity: values.quantity, reason: values.reason, observations: values.observations, performedBy },
        callbacks,
      );
    } else {
      entryMutation.mutate(
        { productId: values.productId, quantity: values.quantity, reason: values.reason, observations: values.observations, performedBy },
        callbacks,
      );
    }
  }

  const movements = movementsQuery.data?.content ?? [];
  const totalPages = Math.max(1, movementsQuery.data?.totalPages ?? 1);

  return (
    <div className={styles.stockPage}>
      <h1>Stock</h1>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Alertas de stock bajo</h2>
        {alertsQuery.isLoading && <Skeleton variant="table" count={3} label="Cargando alertas..." />}
        {alertsQuery.isError && <p role="alert">No se pudieron cargar las alertas de stock.</p>}
        {alertsQuery.isSuccess && (
          <div className={styles.tableWrapper}>
            <StockAlerts products={alertsQuery.data} />
          </div>
        )}
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Registrar movimiento</h2>
        <StockMovementForm
          key={formKey}
          onSubmit={handleMovementSubmit}
          isSubmitting={isSubmitting}
          apiError={apiError}
        />
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Historial de movimientos</h2>
        {movementsQuery.isLoading && <Skeleton variant="table" count={5} label="Cargando movimientos..." />}
        {movementsQuery.isError && <p role="alert">No se pudo cargar el historial de movimientos.</p>}
        {movementsQuery.isSuccess && (
          <>
            <div className={styles.tableWrapper}>
              <MovementsHistoryTable movements={movements} />
            </div>
            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
          </>
        )}
      </section>
    </div>
  );
}
