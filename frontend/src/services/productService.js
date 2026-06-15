import api from './axiosConfig';

export async function getProducts() {
  const { data } = await api.get('/products');
  return data;
}
