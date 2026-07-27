import api from './axiosConfig';

export async function getUsers() {
  const { data } = await api.get('/users');
  return data;
}

export async function getAssignableRoles() {
  const { data } = await api.get('/users/roles');
  return data;
}

export async function createUser(user) {
  const { data } = await api.post('/users', user);
  return data;
}

export async function setUserEnabled(id, enabled) {
  await api.patch(`/users/${id}/status`, { enabled });
}
