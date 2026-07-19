import api from './axiosConfig';

export async function getProductRevisions(productId) {
  const { data } = await api.get(`/audit/products/${productId}/revisions`);
  return data;
}
