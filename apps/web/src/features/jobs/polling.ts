/**
 * How often to re-fetch while skills are still being extracted. With async extraction (R2) a job
 * can wait in the queue for up to ~13 min (retries at 30 s, 2 min, 10 min), so poll fast at first,
 * then slowly, then stop (the page still refreshes on focus).
 */
export const FAST_POLL_MS = 3_000;
export const SLOW_POLL_MS = 30_000;
const FAST_POLLS = 40; // ~2 min
const SLOW_POLLS = 30; // ~15 min more

export function pendingPollInterval(pending: boolean, fetches: number): number | false {
  if (!pending) return false;
  if (fetches < FAST_POLLS) return FAST_POLL_MS;
  if (fetches < FAST_POLLS + SLOW_POLLS) return SLOW_POLL_MS;
  return false;
}
