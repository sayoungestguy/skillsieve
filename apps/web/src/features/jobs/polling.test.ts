import { describe, expect, it } from 'vitest';
import { FAST_POLL_MS, SLOW_POLL_MS, pendingPollInterval } from './polling';

describe('pendingPollInterval', () => {
  it('does not poll when nothing is pending', () => {
    expect(pendingPollInterval(false, 0)).toBe(false);
  });

  it('polls fast, then slowly, then stops', () => {
    expect(pendingPollInterval(true, 1)).toBe(FAST_POLL_MS);
    expect(pendingPollInterval(true, 40)).toBe(SLOW_POLL_MS);
    expect(pendingPollInterval(true, 70)).toBe(false);
  });
});
