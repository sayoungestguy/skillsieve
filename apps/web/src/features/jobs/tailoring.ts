import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { tailoringApi } from '@/api/endpoints';

/** The local model takes 1-2 minutes (up to ~4 with a retry), so poll for about 6 minutes. */
export const TAILOR_POLL_MS = 5_000;
const MAX_POLLS = 72;

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
