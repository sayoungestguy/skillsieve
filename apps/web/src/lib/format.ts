const DAY_MS = 86_400_000;
const RELATIVE_DAYS = 7;
const TIME_ZONE = 'Asia/Singapore';

const dateFmt = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: TIME_ZONE,
});
const dateTimeFmt = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
  timeZone: TIME_ZONE,
});

/** DESIGN.md §6: relative for < 7 days ("3d ago"), else "22 Nov 2026" (SGT). */
export function formatDate(iso: string, now: Date = new Date()): string {
  const d = new Date(iso);
  const days = Math.floor((now.getTime() - d.getTime()) / DAY_MS);
  if (days >= 0 && days < RELATIVE_DAYS) return days === 0 ? 'today' : `${days}d ago`;
  return dateFmt.format(d);
}

export function formatDateTime(iso: string): string {
  return `${dateTimeFmt.format(new Date(iso)).replace(/\s?(am|pm)$/i, (m) => m.toLowerCase())} SGT`;
}

/** Today's date (YYYY-MM-DD) in SGT. */
export function todaySgt(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE }).format(now);
}

export function isDue(nextActionAt: string | null, now: Date = new Date()): boolean {
  return nextActionAt !== null && nextActionAt <= todaySgt(now);
}

export function hostOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url;
  }
}
