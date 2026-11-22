import type { Status } from '@/api/types';

export const STATUSES: readonly Status[] = [
  'SAVED',
  'APPLIED',
  'INTERVIEW',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
] as const;

interface StatusStyle {
  label: string;
  dot: string;
  badge: string;
}

/** DESIGN.md §4.1 status colours. Every status also shows its label. */
export const STATUS_STYLE: Record<Status, StatusStyle> = {
  SAVED: { label: 'Saved', dot: 'bg-neutral', badge: 'bg-slate-100 text-muted' },
  APPLIED: { label: 'Applied', dot: 'bg-accent', badge: 'bg-accent-weak text-indigo-700' },
  INTERVIEW: { label: 'Interview', dot: 'bg-info', badge: 'bg-blue-100 text-blue-700' },
  OFFER: { label: 'Offer', dot: 'bg-success', badge: 'bg-green-100 text-success' },
  REJECTED: { label: 'Rejected', dot: 'bg-danger', badge: 'bg-red-100 text-danger' },
  WITHDRAWN: { label: 'Withdrawn', dot: 'bg-neutral', badge: 'bg-slate-100 text-muted' },
};

/** Next/previous column for keyboard moves (Shift+Arrow). */
export function adjacentStatus(status: Status, dir: 1 | -1): Status | null {
  const i = STATUSES.indexOf(status) + dir;
  return STATUSES[i] ?? null;
}
