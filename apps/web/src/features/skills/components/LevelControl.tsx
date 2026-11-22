import type { Level } from '@/api/types';
import { LEVELS, LEVEL_LABEL, LEVEL_SEGMENT_ON } from '@/lib/levels';

interface LevelControlProps {
  skillName: string;
  slug: string;
  value: Level;
  onChange: (level: Level) => void;
}

/** 0–3 segmented control with radio-group semantics (DESIGN.md §5). */
export function LevelControl({ skillName, slug, value, onChange }: LevelControlProps) {
  return (
    <fieldset className="m-0 flex border-0 p-0">
      <legend className="sr-only">Your level in {skillName}</legend>
      {LEVELS.map((l, i) => {
        const on = value === l;
        return (
          <label
            key={l}
            className={`relative -ml-px inline-flex min-h-11 min-w-[76px] cursor-pointer items-center justify-center border px-2.5 text-[0.8125rem] font-semibold focus-within:z-10 focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-accent ${
              i === 0 ? 'ml-0 rounded-l-chip' : ''
            } ${i === LEVELS.length - 1 ? 'rounded-r-chip' : ''} ${
              on
                ? `z-[1] ${LEVEL_SEGMENT_ON[l]}`
                : 'border-slate-300 bg-surface text-muted hover:bg-bg'
            }`}
          >
            <input
              className="sr-only"
              type="radio"
              name={`lvl-${slug}`}
              checked={on}
              onChange={() => onChange(l)}
            />
            {LEVEL_LABEL[l]}
          </label>
        );
      })}
    </fieldset>
  );
}
