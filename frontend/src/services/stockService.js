import api from './axiosConfig';

export async function getMovements(params = {}) {
  const { data } = await api.get('/stock/movements', { params });
  return data;
}

export async function getMovementsByProduct(productId, params = {}) {
  const { data } = await api.get(`/stock/movements/${productId}`, { params });
  return data;
}

export async function getStockAlerts() {
  const { data } = await api.get('/stock/alerts');
  return data;
}

export async function registerEntry(movement) {
  const { data } = await api.post('/stock/entry', movement);
  return data;
}

export async function registerExit(movement) {
  const { data } = await api.post('/stock/exit', movement);
  return data;
}

export async function adjustStock(adjustment) {
  const { data } = await api.post('/stock/adjust', adjustment);
  return data;
}
