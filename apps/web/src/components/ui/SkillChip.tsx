import type { Importance, Level } from '@/api/types';
import { LEVEL_DOT, LEVEL_LABEL } from '@/lib/levels';

interface SkillChipProps {
  name: string;
  importance: Importance;
  level: Level;
}

export function SkillChip({ name, importance, level }: SkillChipProps) {
  const pref = importance === 'PREFERRED';
  return (
    <span
      className={`chip chip-lg ${pref ? 'chip-pref' : ''}`}
      title={`My level: ${LEVEL_LABEL[level]}`}
    >
      <span className={`dot ${LEVEL_DOT[level]}`} aria-hidden="true" />
      {name}
      <span className="sr-only">
        , {pref ? 'preferred' : 'required'}, my level {LEVEL_LABEL[level]}
      </span>
    </span>
  );
}
