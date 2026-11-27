import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { tokensApi } from '@/api/endpoints';

export const tokensKey = ['tokens'] as const;

export function useTokens() {
  return useQuery({ queryKey: tokensKey, queryFn: tokensApi.list });
}

export function useCreateToken() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => tokensApi.create(name),
    onSuccess: () => qc.invalidateQueries({ queryKey: tokensKey }),
  });
}

export function useRevokeToken() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => tokensApi.revoke(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: tokensKey }),
  });
}
