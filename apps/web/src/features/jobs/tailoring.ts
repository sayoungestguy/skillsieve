import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { tailoringApi } from '@/api/endpoints';

/**
 * The local model takes 1-5 minutes on a slow CPU (up to ~10 with a retry), so poll for about 15
 * minutes, matching core-api's 15-minute expiry for a stuck request.
 */
export const TAILOR_POLL_MS = 5_000;
const MAX_POLLS = 180;

export const tailoringKey = (jobId: string) => ['tailoring', jobId] as const;

export function useTailoring(jobId: string) {
  return useQuery({
    queryKey: tailoringKey(jobId),
    queryFn: () => tailoringApi.get(jobId),
    refetchInterval: (q) =>
      q.state.data?.status === 'PENDING' && q.state.dataUpdateCount < MAX_POLLS
        ? TAILOR_POLL_MS
        : false,
  });
}

export function useRequestTailoring(jobId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => tailoringApi.request(jobId),
    onSuccess: (t) => qc.setQueryData(tailoringKey(jobId), t),
  });
}
