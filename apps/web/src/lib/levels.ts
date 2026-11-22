import type { Level } from '@/api/types';

export const LEVELS: readonly Level[] = [0, 1, 2, 3];
export const LEVEL_LABEL: Record<Level, string> = {
  0: 'None',
  1: 'Aware',
  2: 'Working',
  3: 'Strong',
};
export const LEVEL_DOT: Record<Level, string> = {
  0: 'bg-neutral',
  1: 'bg-warning',
  2: 'bg-info',
  3: 'bg-success',
};
export const LEVEL_SEGMENT_ON: Record<Level, string> = {
  0: 'bg-slate-100 text-ink border-slate-500',
  1: 'bg-amber-100 text-amber-800 border-warning',
  2: 'bg-blue-100 text-blue-700 border-info',
  3: 'bg-green-100 text-green-800 border-success',
};

export const GAP_MIN_PCT = 30;
export const GAP_MAX_LEVEL: Level = 1;

/** Gap rule (DESIGN.md §3.4): frequency ≥ 30% and my level ≤ 1. */
export function isGap(totalPct: number, level: Level): boolean {
  return totalPct >= GAP_MIN_PCT && level <= GAP_MAX_LEVEL;
}
