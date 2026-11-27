import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { applicationsApi, jobsApi } from '@/api/endpoints';
import type { CreateJobRequest, UpdateApplicationRequest } from '@/api/types';
import { pendingPollInterval } from './polling';

export const jobKey = (id: string) => ['jobs', 'detail', id] as const;
export const historyKey = (appId: string) => ['applications', appId, 'history'] as const;

export function useJob(id: string) {
  return useQuery({
    queryKey: jobKey(id),
    queryFn: () => jobsApi.get(id),
    // Poll while skills are still being extracted (not when skills-svc said "try later").
    refetchInterval: (q) =>
      pendingPollInterval(
        q.state.data?.extractionStatus === 'PENDING' && !q.state.data.extractionError,
        q.state.dataUpdateCount,
      ),
  });
}

export function useHistory(appId: string | undefined) {
  return useQuery({
    queryKey: historyKey(appId ?? ''),
    queryFn: () => applicationsApi.history(appId ?? ''),
    enabled: !!appId,
  });
}

export function useCreateJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateJobRequest) => jobsApi.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['jobs'] }),
  });
}

export function useUpdateApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ appId, body }: { appId: string; body: UpdateApplicationRequest }) =>
      applicationsApi.update(appId, body),
    onSuccess: (app) => {
      qc.invalidateQueries({ queryKey: ['jobs'] });
      qc.invalidateQueries({ queryKey: historyKey(app.id) });
    },
  });
}

export function useRetryExtraction(jobId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => jobsApi.retryExtraction(jobId),
    onSuccess: () => qc.invalidateQueries({ queryKey: jobKey(jobId) }),
  });
}
