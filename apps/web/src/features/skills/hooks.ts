import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { skillsApi } from '@/api/endpoints';
import type { Level, SkillDto } from '@/api/types';

export const skillsKey = ['skills'] as const;

export function useSkills() {
  return useQuery({ queryKey: skillsKey, queryFn: () => skillsApi.list() });
}

export function useSetLevel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ slug, level }: { slug: string; level: Level }) =>
      skillsApi.setLevel(slug, level),
    onMutate: async ({ slug, level }) => {
      await qc.cancelQueries({ queryKey: skillsKey });
      const prev = qc.getQueryData<SkillDto[]>(skillsKey);
      qc.setQueryData<SkillDto[]>(skillsKey, (old) =>
        old?.map((s) => (s.slug === slug ? { ...s, myLevel: level } : s)),
      );
      return { prev };
    },
    onError: (_e, _v, ctx) => ctx?.prev && qc.setQueryData(skillsKey, ctx.prev),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: skillsKey });
      qc.invalidateQueries({ queryKey: ['insights'] });
      qc.invalidateQueries({ queryKey: ['jobs', 'detail'] });
    },
  });
}
