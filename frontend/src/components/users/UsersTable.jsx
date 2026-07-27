import { useState } from 'react';
import ConfirmDialog from '../common/ConfirmDialog';
import { useSetUserEnabled } from '../../hooks/useUsers';
import { useToast } from '../../hooks/useToast';
import styles from '../../styles/users.module.css';

export default function UsersTable({ users }) {
  const setEnabledMutation = useSetUserEnabled();
  const { showToast } = useToast();
  const [pendingDisable, setPendingDisable] = useState(null);

  function requestDisable(id, username) {
    setPendingDisable({ id, username });
  }

  function cancelDisable() {
    setPendingDisable(null);
  }

  function confirmDisable() {
    const { id } = pendingDisable;
    setPendingDisable(null);
    setEnabledMutation.mutate(
      { id, enabled: false },
      {
        onSuccess: () => showToast('Usuario deshabilitado correctamente.'),
        onError: () => showToast('No se pudo deshabilitar el usuario.', { type: 'error' }),
      },
    );
  }

  function enableUser(id) {
    setEnabledMutation.mutate(
      { id, enabled: true },
      {
        onSuccess: () => showToast('Usuario habilitado correctamente.'),
        onError: () => showToast('No se pudo habilitar el usuario.', { type: 'error' }),
      },
    );
  }

  if (users.length === 0) {
    return <p>No se encontraron usuarios.</p>;
  }

  return (
    <>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>Email</th>
            <th>Nombre</th>
            <th>Rol</th>
            <th>Estado</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          {users.map((user) => (
            <tr key={user.id}>
              <td>{user.username}</td>
              <td>{user.firstName} {user.lastName}</td>
              <td>{user.role ?? 'Sin rol'}</td>
              <td>
                {user.enabled ? (
                  <span className={styles.badgeSuccess}>Habilitado</span>
                ) : (
                  <span className={styles.badgeWarning}>Deshabilitado</span>
                )}
              </td>
              <td>
                {user.enabled ? (
                  <button
                    type="button"
                    className={styles.disableButton}
                    onClick={() => requestDisable(user.id, user.username)}
                    disabled={setEnabledMutation.isPending}
                  >
                    Deshabilitar
                  </button>
                ) : (
                  <button
                    type="button"
                    className={styles.enableButton}
                    onClick={() => enableUser(user.id)}
                    disabled={setEnabledMutation.isPending}
                  >
                    Habilitar
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <ConfirmDialog
        open={pendingDisable !== null}
        message={pendingDisable ? `¿Deshabilitar al usuario "${pendingDisable.username}"? No podrá iniciar sesión hasta que lo vuelvas a habilitar.` : ''}
        confirmLabel="Deshabilitar"
        cancelLabel="Cancelar"
        onConfirm={confirmDisable}
        onCancel={cancelDisable}
      />
    </>
  );
}
