import { useForm } from 'react-hook-form';
import styles from '../../styles/forms.module.css';

const EMPTY_VALUES = {
  email: '',
  firstName: '',
  lastName: '',
  password: '',
  role: '',
};

export default function UserForm({ roles, onSubmit, isSubmitting, apiError }) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({ defaultValues: EMPTY_VALUES });

  function submitHandler(values) {
    onSubmit(
      {
        email: values.email.trim(),
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        password: values.password,
        role: values.role,
      },
      () => reset(EMPTY_VALUES),
    );
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit(submitHandler)} noValidate>
      {apiError && (
        <p role="alert" className={styles.apiError}>
          {apiError}
        </p>
      )}

      <div className={styles.field}>
        <label htmlFor="user-email">Email</label>
        <input
          id="user-email"
          type="email"
          {...register('email', {
            required: 'El email es obligatorio',
            pattern: { value: /^\S+@\S+\.\S+$/, message: 'Ingresa un email válido' },
          })}
        />
        {errors.email && <span className={styles.fieldError}>{errors.email.message}</span>}
      </div>

      <div className={styles.field}>
        <label htmlFor="user-firstName">Nombre</label>
        <input
          id="user-firstName"
          type="text"
          {...register('firstName', { required: 'El nombre es obligatorio' })}
        />
        {errors.firstName && <span className={styles.fieldError}>{errors.firstName.message}</span>}
      </div>

      <div className={styles.field}>
        <label htmlFor="user-lastName">Apellido</label>
        <input
          id="user-lastName"
          type="text"
          {...register('lastName', { required: 'El apellido es obligatorio' })}
        />
        {errors.lastName && <span className={styles.fieldError}>{errors.lastName.message}</span>}
      </div>

      <div className={styles.field}>
        <label htmlFor="user-password">Contraseña temporal</label>
        <input
          id="user-password"
          type="password"
          {...register('password', {
            required: 'La contraseña es obligatoria',
            minLength: { value: 8, message: 'Mínimo 8 caracteres' },
          })}
        />
        {errors.password && <span className={styles.fieldError}>{errors.password.message}</span>}
      </div>

      <div className={styles.field}>
        <label htmlFor="user-role">Rol</label>
        <select id="user-role" {...register('role', { required: 'El rol es obligatorio' })}>
          <option value="">Selecciona un rol</option>
          {(roles ?? []).map((role) => (
            <option key={role} value={role}>
              {role}
            </option>
          ))}
        </select>
        {errors.role && <span className={styles.fieldError}>{errors.role.message}</span>}
      </div>

      <div className={styles.actions}>
        <button type="submit" disabled={isSubmitting}>
          Crear usuario
        </button>
      </div>
    </form>
  );
}
