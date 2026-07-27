import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createUser, getAssignableRoles, getUsers, setUserEnabled } from '../services/userService';

export function useUsers() {
  return useQuery({
    queryKey: ['users'],
    queryFn: getUsers,
  });
}

export function useAssignableRoles() {
  return useQuery({
    queryKey: ['users', 'roles'],
    queryFn: getAssignableRoles,
  });
}

export function useCreateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
}

export function useSetUserEnabled() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, enabled }) => setUserEnabled(id, enabled),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
}
