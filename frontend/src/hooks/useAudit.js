import { useQuery } from '@tanstack/react-query';
import { getProductRevisions } from '../services/auditService';

export function useProductRevisions(productId) {
  return useQuery({
    queryKey: ['audit', 'products', productId, 'revisions'],
    queryFn: () => getProductRevisions(productId),
    enabled: Boolean(productId),
  });
}
