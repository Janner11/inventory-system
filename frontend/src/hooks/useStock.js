import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  adjustStock,
  getMovements,
  getMovementsByProduct,
  getStockAlerts,
  registerEntry,
  registerExit,
} from '../services/stockService';

export function useStockMovements(params = {}) {
  return useQuery({
    queryKey: ['stock', 'movements', params],
    queryFn: () => getMovements(params),
  });
}

export function useProductMovements(productId, params = {}) {
  return useQuery({
    queryKey: ['stock', 'movements', 'product', productId, params],
    queryFn: () => getMovementsByProduct(productId, params),
    enabled: Boolean(productId),
  });
}

export function useStockAlerts() {
  return useQuery({
    queryKey: ['stock', 'alerts'],
    queryFn: getStockAlerts,
  });
}

// Registrar un movimiento cambia la cantidad del producto (Productos/Dashboard también
// muestran ese dato), por eso invalida 'products' además de 'stock' en las 3 mutaciones.
function useStockMutation(mutationFn) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
    },
  });
}

export function useRegisterEntry() {
  return useStockMutation(registerEntry);
}

export function useRegisterExit() {
  return useStockMutation(registerExit);
}

export function useAdjustStock() {
  return useStockMutation(adjustStock);
}
