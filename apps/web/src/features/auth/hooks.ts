import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '@/api/client';
import { authApi } from '@/api/endpoints';
import type { LoginRequest, MeDto } from '@/api/types';

export const meKey = ['me'] as const;

/** The signed-in user, or null when there is no valid session (401). */
export function useMe() {
  return useQuery<MeDto | null>({
    queryKey: meKey,
    queryFn: async () => {
      try {
        return await authApi.me();
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null;
        throw e;
      }
    },
    staleTime: Infinity,
    retry: false,
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: LoginRequest) => authApi.login(body),
    onSuccess: async () => {
      qc.removeQueries({ predicate: (q) => q.queryKey[0] !== meKey[0] });
      await qc.invalidateQueries({ queryKey: meKey });
    },
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      qc.clear();
      qc.setQueryData(meKey, null);
    },
  });
}

/** Demo visitors can look but not change anything (core-api returns 403 for writes). */
export function useCanEdit(): boolean {
  const { data } = useMe();
  return !!data && data.role !== 'DEMO';
}
