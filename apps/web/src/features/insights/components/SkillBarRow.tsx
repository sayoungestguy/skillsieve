import type { SkillInsightDto } from '@/api/types';
import { LevelLabel } from '@/components/ui/LevelDot';
import { LEVEL_LABEL } from '@/lib/levels';

interface SkillBarRowProps {
  row: SkillInsightDto;
  selected: boolean;
  onSelect: () => void;
}

export const BAR_GRID = 'grid grid-cols-[140px_minmax(0,1fr)_52px_128px_56px] items-center gap-4';

/** One bar-chart row: required (solid) + preferred (light) share, value also as text. */
export function SkillBarRow({ row, selected, onSelect }: SkillBarRowProps) {
  const label = `${row.name}: in ${row.totalPct}% of posts, ${row.requiredPct}% required, ${row.preferredPct}% preferred. My level ${LEVEL_LABEL[row.myLevel]}.${row.isGap ? ' Gap.' : ''} Show posts.`;
  return (
    <button
      type="button"
      aria-pressed={selected}
      aria-label={label}
      onClick={onSelect}
      className={`${BAR_GRID} min-h-12 w-full cursor-pointer rounded-chip border-0 px-3 py-1.5 text-left text-[0.9375rem] text-ink ${
        selected ? 'bg-accent-weak' : 'bg-transparent hover:bg-bg'
      }`}
    >
      <span className="font-semibold">{row.name}</span>
      <span className="flex h-3.5 overflow-hidden rounded bg-slate-100" aria-hidden="true">
        <span className="bg-accent" style={{ width: `${row.requiredPct}%` }} />
        <span className="bg-indigo-300" style={{ width: `${row.preferredPct}%` }} />
      </span>
      <span className="text-right font-semibold tabular-nums">{row.totalPct}%</span>
      <LevelLabel level={row.myLevel} />
      <span>{row.isGap && <span className="gap-badge">GAP</span>}</span>
    </button>
  );
}
