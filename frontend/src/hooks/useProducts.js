import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createProduct,
  deleteProduct,
  getCriticalProducts,
  getProductById,
  getProducts,
  updateProduct,
} from '../services/productService';

export function useProducts(params = {}, options = {}) {
  return useQuery({
    queryKey: ['products', params],
    queryFn: () => getProducts(params),
    placeholderData: keepPreviousData,
    ...options,
  });
}

// Categorías de todo el catálogo (independiente de la página/filtros actuales), para el <select> de ProductFilters.
export function useProductCategories() {
  return useQuery({
    queryKey: ['products', 'categories'],
    queryFn: () => getProducts({ size: 100 }),
    select: (page) => [...new Set(page.content.map((product) => product.category))].sort(),
  });
}

export function useCriticalProducts(options = {}) {
  return useQuery({
    queryKey: ['products', 'critical'],
    queryFn: getCriticalProducts,
    ...options,
  });
}

export function useProduct(id) {
  return useQuery({
    queryKey: ['products', id],
    queryFn: () => getProductById(id),
    enabled: Boolean(id),
  });
}

export function useCreateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createProduct,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
    },
  });
}

export function useUpdateProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }) => updateProduct(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
    },
  });
}

export function useDeleteProduct() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => deleteProduct(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
    },
  });
}
