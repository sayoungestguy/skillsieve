import type { Level } from '@/api/types';
import { LEVEL_DOT, LEVEL_LABEL } from '@/lib/levels';

interface LevelLabelProps {
  level: Level;
  className?: string;
}

/** Coloured dot + text label, so colour is never the only signal. */
export function LevelLabel({ level, className = 'text-sm' }: LevelLabelProps) {
  return (
    <span className={`inline-flex items-center gap-1.5 ${className}`}>
      <span className={`dot ${LEVEL_DOT[level]}`} aria-hidden="true" />
      {LEVEL_LABEL[level]}
    </span>
  );
}
