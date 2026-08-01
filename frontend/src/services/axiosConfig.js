import axios from 'axios';
import keycloak from './keycloak';
import { VITE_API_BASE_URL } from '../config/env';

const api = axios.create({
  baseURL: VITE_API_BASE_URL,
});

api.interceptors.request.use(async (config) => {
  if (keycloak.token) {
    try {
      await keycloak.updateToken(30);
    } catch {
      keycloak.login();
    }
    config.headers.Authorization = `Bearer ${keycloak.token}`;
  }
  return config;
});

export default api;
