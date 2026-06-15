import { useNavigate, useParams } from 'react-router-dom';
import ProductForm from '../components/products/ProductForm';
import { useCreateProduct, useProduct, useUpdateProduct } from '../hooks/useProducts';
import styles from '../styles/forms.module.css';

function extractErrorMessage(error) {
  return error?.response?.data?.message || 'Ocurrió un error inesperado. Intenta nuevamente.';
}

export default function ProductFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditMode = Boolean(id);

  const { data: product, isLoading, isError } = useProduct(id);
  const createMutation = useCreateProduct();
  const updateMutation = useUpdateProduct();

  const activeMutation = isEditMode ? updateMutation : createMutation;

  function handleSubmit(values) {
    if (isEditMode) {
      updateMutation.mutate(
        { id, data: values },
        { onSuccess: () => navigate('/products') },
      );
    } else {
      createMutation.mutate(values, { onSuccess: () => navigate('/products') });
    }
  }

  function handleCancel() {
    navigate('/products');
  }

  if (isEditMode && isLoading) {
    return <p>Cargando producto...</p>;
  }

  if (isEditMode && isError) {
    return <p role="alert">No se pudo cargar el producto solicitado.</p>;
  }

  return (
    <div className={styles.formPage}>
      <h1>{isEditMode ? 'Editar producto' : 'Nuevo producto'}</h1>
      <ProductForm
        initialValues={isEditMode ? product : undefined}
        onSubmit={handleSubmit}
        onCancel={handleCancel}
        isSubmitting={activeMutation.isPending}
        apiError={activeMutation.isError ? extractErrorMessage(activeMutation.error) : null}
      />
    </div>
  );
}
