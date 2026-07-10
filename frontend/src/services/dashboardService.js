import api from './axiosConfig';

export async function getSummary() {
  const { data } = await api.get('/dashboard/summary');
  return data;
}

export async function getCriticalProducts() {
  const { data } = await api.get('/dashboard/critical-products');
  return data;
}

export async function getRecentMovements() {
  const { data } = await api.get('/dashboard/recent-movements');
  return data;
}

export async function getTopProducts() {
  const { data } = await api.get('/dashboard/top-products');
  return data;
}
