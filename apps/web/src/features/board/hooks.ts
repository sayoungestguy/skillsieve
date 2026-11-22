import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { applicationsApi, jobsApi } from '@/api/endpoints';
import type { JobSummaryDto, Page, Status } from '@/api/types';

export const boardKey = ['jobs', 'board'] as const;

export function useBoard() {
  return useQuery({ queryKey: boardKey, queryFn: () => jobsApi.list() });
}

interface MoveArgs {
  job: JobSummaryDto;
  to: Status;
}

/** Optimistic status change; rolls back on error (e.g. 409 stale version). */
export function useMoveJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ job, to }: MoveArgs) =>
      applicationsApi.update(job.applicationId, { status: to, version: job.version }),
    onMutate: async ({ job, to }) => {
      await qc.cancelQueries({ queryKey: boardKey });
      const prev = qc.getQueryData<Page<JobSummaryDto>>(boardKey);
      qc.setQueryData<Page<JobSummaryDto>>(
        boardKey,
        (old) =>
          old && {
            ...old,
            content: old.content.map((j) => (j.id === job.id ? { ...j, status: to } : j)),
          },
      );
      return { prev };
    },
    onError: (_e, _v, ctx) => ctx?.prev && qc.setQueryData(boardKey, ctx.prev),
    onSettled: () => qc.invalidateQueries({ queryKey: ['jobs'] }),
  });
}
