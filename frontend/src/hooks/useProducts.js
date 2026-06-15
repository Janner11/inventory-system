import { useQuery } from '@tanstack/react-query';
import { getProducts } from '../services/productService';

export function useProducts() {
  return useQuery({
    queryKey: ['products'],
    queryFn: getProducts,
  });
}
