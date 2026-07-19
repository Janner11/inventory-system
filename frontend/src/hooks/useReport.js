import { useQuery } from '@tanstack/react-query';
import { getInventoryReport } from '../services/reportService';

export function useInventoryReport() {
  return useQuery({ queryKey: ['reports', 'inventory'], queryFn: getInventoryReport });
}
