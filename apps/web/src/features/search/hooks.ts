import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { searchApi } from '@/api/endpoints';

export function useSearch(q: string) {
  const term = q.trim();
  return useQuery({
    queryKey: ['search', term],
    queryFn: () => searchApi.search(term),
    enabled: term.length > 0,
    placeholderData: keepPreviousData,
  });
}
