import { useForm } from 'react-hook-form';
import { useProducts } from '../../hooks/useProducts';
import styles from '../../styles/stock.module.css';

const EMPTY_VALUES = {
  type: 'ENTRY',
  productId: '',
  quantity: '',
  newQuantity: '',
  reason: '',
  observations: '',
};

export default function StockMovementForm({ onSubmit, isSubmitting, apiError }) {
  const { data: productsPage } = useProducts({ size: 100 });
  const products = productsPage?.content ?? [];

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm({ defaultValues: EMPTY_VALUES });

  const type = watch('type');
  const isAdjustment = type === 'ADJUSTMENT';

  function submitHandler(values) {
    onSubmit({
      type: values.type,
      productId: values.productId,
      quantity: values.quantity,
      newQuantity: values.newQuantity,
      reason: values.reason.trim(),
      observations: values.observations.trim(),
    });
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit(submitHandler)} noValidate>
      {apiError && (
        <p role="alert" className={styles.apiError}>
          {apiError}
        </p>
      )}

      <div className={styles.formGrid}>
        <div className={styles.field}>
          <label htmlFor="type">Tipo de movimiento</label>
          <select id="type" {...register('type')}>
            <option value="ENTRY">Entrada</option>
            <option value="EXIT">Salida</option>
            <option value="ADJUSTMENT">Ajuste</option>
          </select>
        </div>

        <div className={styles.field}>
          <label htmlFor="productId">Producto</label>
          <select id="productId" {...register('productId', { required: 'El producto es obligatorio' })}>
            <option value="">Selecciona un producto</option>
            {products.map((product) => (
              <option key={product.id} value={product.id}>
                {product.sku} — {product.name} (stock: {product.quantity})
              </option>
            ))}
          </select>
          {errors.productId && <span className={styles.fieldError}>{errors.productId.message}</span>}
        </div>

        {isAdjustment ? (
          <div className={styles.field}>
            <label htmlFor="newQuantity">Nueva cantidad</label>
            <input
              id="newQuantity"
              type="number"
              step="1"
              {...register('newQuantity', {
                required: 'La nueva cantidad es obligatoria',
                valueAsNumber: true,
                validate: (value) => value >= 0 || 'La nueva cantidad no puede ser negativa',
              })}
            />
            {errors.newQuantity && <span className={styles.fieldError}>{errors.newQuantity.message}</span>}
          </div>
        ) : (
          <div className={styles.field}>
            <label htmlFor="quantity">Cantidad</label>
            <input
              id="quantity"
              type="number"
              step="1"
              {...register('quantity', {
                required: 'La cantidad es obligatoria',
                valueAsNumber: true,
                validate: (value) => value > 0 || 'La cantidad debe ser mayor que 0',
              })}
            />
            {errors.quantity && <span className={styles.fieldError}>{errors.quantity.message}</span>}
          </div>
        )}

        <div className={styles.field}>
          <label htmlFor="reason">Motivo</label>
          <input
            id="reason"
            type="text"
            {...register('reason', { maxLength: { value: 200, message: 'Máximo 200 caracteres' } })}
          />
          {errors.reason && <span className={styles.fieldError}>{errors.reason.message}</span>}
        </div>

        <div className={styles.field}>
          <label htmlFor="observations">Observaciones</label>
          <textarea
            id="observations"
            rows={2}
            {...register('observations', { maxLength: { value: 500, message: 'Máximo 500 caracteres' } })}
          />
          {errors.observations && <span className={styles.fieldError}>{errors.observations.message}</span>}
        </div>
      </div>

      <div className={styles.actions}>
        <button type="submit" disabled={isSubmitting}>
          Registrar movimiento
        </button>
      </div>
    </form>
  );
}
