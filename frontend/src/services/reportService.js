import api from './axiosConfig';

export async function getInventoryReport() {
  const { data } = await api.get('/reports/inventory');
  return data;
}
