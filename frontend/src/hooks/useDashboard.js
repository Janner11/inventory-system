import { useQuery } from '@tanstack/react-query';
import {
  getCriticalProducts,
  getRecentMovements,
  getSummary,
  getTopProducts,
} from '../services/dashboardService';

export function useDashboardSummary() {
  return useQuery({ queryKey: ['dashboard', 'summary'], queryFn: getSummary });
}

export function useDashboardCriticalProducts() {
  return useQuery({ queryKey: ['dashboard', 'critical-products'], queryFn: getCriticalProducts });
}

export function useDashboardRecentMovements() {
  return useQuery({ queryKey: ['dashboard', 'recent-movements'], queryFn: getRecentMovements });
}

export function useDashboardTopProducts() {
  return useQuery({ queryKey: ['dashboard', 'top-products'], queryFn: getTopProducts });
}
