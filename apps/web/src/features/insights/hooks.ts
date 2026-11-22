import { useQuery } from '@tanstack/react-query';
import { insightsApi, searchApi } from '@/api/endpoints';
import type { Status } from '@/api/types';

export interface InsightFilters {
  status?: Status;
  from?: string;
}

export function useSkillInsights(filters: InsightFilters) {
  return useQuery({
    queryKey: ['insights', 'skills', filters],
    queryFn: () => insightsApi.skills(filters),
  });
}

export function useInsightSummary() {
  return useQuery({ queryKey: ['insights', 'summary'], queryFn: insightsApi.summary });
}

/** Posts that mention a skill (uses full-text search). */
export function usePostsWithSkill(name: string | undefined) {
  return useQuery({
    queryKey: ['search', name],
    queryFn: () => searchApi.search(name ?? ''),
    enabled: !!name,
  });
}
