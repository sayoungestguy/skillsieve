import type { JobSkill } from '@/api/types';
import { SkillChip } from '@/components/ui/SkillChip';

interface SkillGroupProps {
  label: string;
  skills: JobSkill[];
}

export function SkillGroup({ label, skills }: SkillGroupProps) {
  if (skills.length === 0) return null;
  return (
    <div>
      <h3 className="mb-2.5 text-xs font-semibold uppercase tracking-[.06em] text-muted">
        {label} · {skills.length}
      </h3>
      <div className="flex flex-wrap gap-2">
        {skills.map((s) => (
          <SkillChip key={s.slug} name={s.name} importance={s.importance} level={s.myLevel} />
        ))}
      </div>
    </div>
  );
}
