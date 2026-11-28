import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { resumeApi, tokensApi } from '@/api/endpoints';

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

export const resumeKey = ['resume'] as const;

export function useResume() {
  return useQuery({ queryKey: resumeKey, queryFn: resumeApi.get, staleTime: Infinity });
}

export function useSaveResume() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (text: string) => resumeApi.save(text),
    onSuccess: (saved) => qc.setQueryData(resumeKey, saved),
  });
}

export function useDeleteResume() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => resumeApi.remove(),
    onSuccess: () => qc.setQueryData(resumeKey, null),
  });
}
