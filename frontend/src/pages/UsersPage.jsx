import { useState } from 'react';
import Skeleton from '../components/common/Skeleton';
import UserForm from '../components/users/UserForm';
import UsersTable from '../components/users/UsersTable';
import { useAssignableRoles, useCreateUser, useUsers } from '../hooks/useUsers';
import { useToast } from '../hooks/useToast';
import styles from '../styles/users.module.css';

function extractErrorMessage(error) {
  return error?.response?.data?.message || 'Ocurrió un error inesperado. Intenta nuevamente.';
}

export default function UsersPage() {
  const { showToast } = useToast();
  const [apiError, setApiError] = useState(null);

  const usersQuery = useUsers();
  const rolesQuery = useAssignableRoles();
  const createMutation = useCreateUser();

  function handleCreateUser(values, resetForm) {
    setApiError(null);
    createMutation.mutate(values, {
      onSuccess: () => {
        showToast('Usuario creado correctamente.');
        resetForm();
      },
      onError: (error) => {
        const message = extractErrorMessage(error);
        setApiError(message);
        showToast(message, { type: 'error' });
      },
    });
  }

  return (
    <div className={styles.usersPage}>
      <h1>Usuarios</h1>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Nuevo usuario</h2>
        <UserForm
          roles={rolesQuery.data}
          onSubmit={handleCreateUser}
          isSubmitting={createMutation.isPending}
          apiError={apiError}
        />
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Usuarios existentes</h2>
        {usersQuery.isLoading && <Skeleton variant="table" count={5} label="Cargando usuarios..." />}
        {usersQuery.isError && <p role="alert">No se pudieron cargar los usuarios.</p>}
        {usersQuery.isSuccess && (
          <div className={styles.tableWrapper}>
            <UsersTable users={usersQuery.data} />
          </div>
        )}
      </section>
    </div>
  );
}
