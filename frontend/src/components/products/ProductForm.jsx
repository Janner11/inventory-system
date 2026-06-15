import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import styles from '../../styles/forms.module.css';

const EMPTY_VALUES = {
  name: '',
  sku: '',
  description: '',
  category: '',
  price: '',
  quantity: '',
  minStock: '',
};

export default function ProductForm({ initialValues, onSubmit, onCancel, isSubmitting, apiError }) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({ defaultValues: EMPTY_VALUES });

  useEffect(() => {
    if (initialValues) {
      reset({
        name: initialValues.name ?? '',
        sku: initialValues.sku ?? '',
        description: initialValues.description ?? '',
        category: initialValues.category ?? '',
        price: initialValues.price ?? '',
        quantity: initialValues.quantity ?? '',
        minStock: initialValues.minStock ?? '',
      });
    }
  }, [initialValues, reset]);

  function submitHandler(values) {
    onSubmit({
      name: values.name.trim(),
      sku: values.sku.trim().toUpperCase(),
      description: values.description.trim(),
      category: values.category.trim(),
      price: values.price,
      quantity: values.quantity,
      minStock: values.minStock,
    });
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit(submitHandler)} noValidate>
      {apiError && (
        <p role="alert" className={styles.apiError}>
          {apiError}
        </p>
      )}

      <div className={styles.field}>
        <label htmlFor="name">Nombre</label>
        <input
          id="name"
          type="text"
          {...register('name', {
            required: 'El nombre es obligatorio',
            maxLength: { value: 200, message: 'Máximo 200 caracteres' },
          })}
        />
        {errors.name && <span className={styles.fieldError}>{errors.name.message}</span>}
      </div>

      <div className={styles.field}>
        <label htmlFor="sku">SKU</label>
        <input
          id="sku"
          type="text"
          {...register('sku', {
            required: 'El SKU es obligatorio',
            maxLength: { value: 50, message: 'Máximo 50 caracteres' },
          })}
        />
        {errors.sku && <span className={styles.fieldError}>{errors.sku.message}</span>}
      </div>

      <div className={styles.field}>
        <label htmlFor="description">Descripción</label>
        <textarea
          id="description"
          rows={3}
          {...register('description', {
            maxLength: { value: 1000, message: 'Máximo 1000 caracteres' },
          })}
        />
        {errors.description && <span className={styles.fieldError}>{errors.description.message}</span>}
      </div>

      <div className={styles.field}>
        <label htmlFor="category">Categoría</label>
        <input
          id="category"
          type="text"
          {...register('category', {
            required: 'La categoría es obligatoria',
            maxLength: { value: 100, message: 'Máximo 100 caracteres' },
          })}
        />
        {errors.category && <span className={styles.fieldError}>{errors.category.message}</span>}
      </div>

      <div className={styles.field}>
        <label htmlFor="price">Precio</label>
        <input
          id="price"
          type="number"
          step="0.01"
          {...register('price', {
            required: 'El precio es obligatorio',
            valueAsNumber: true,
            validate: (value) => value > 0 || 'El precio debe ser mayor que 0',
          })}
        />
        {errors.price && <span className={styles.fieldError}>{errors.price.message}</span>}
      </div>

      <div className={styles.field}>
        <label htmlFor="quantity">Cantidad</label>
        <input
          id="quantity"
          type="number"
          step="1"
          {...register('quantity', {
            required: 'La cantidad es obligatoria',
            valueAsNumber: true,
            validate: (value) => value >= 0 || 'La cantidad no puede ser negativa',
          })}
        />
        {errors.quantity && <span className={styles.fieldError}>{errors.quantity.message}</span>}
      </div>

      <div className={styles.field}>
        <label htmlFor="minStock">Stock mínimo</label>
        <input
          id="minStock"
          type="number"
          step="1"
          {...register('minStock', {
            required: 'El stock mínimo es obligatorio',
            valueAsNumber: true,
            validate: (value) => value >= 0 || 'El stock mínimo no puede ser negativo',
          })}
        />
        {errors.minStock && <span className={styles.fieldError}>{errors.minStock.message}</span>}
      </div>

      <div className={styles.actions}>
        <button type="submit" disabled={isSubmitting}>
          Guardar
        </button>
        <button type="button" className={styles.cancelButton} onClick={onCancel} disabled={isSubmitting}>
          Cancelar
        </button>
      </div>
    </form>
  );
}
