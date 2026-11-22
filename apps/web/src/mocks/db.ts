import type { JobDto, Level, StatusEventDto } from '@/api/types';
import { buildJobs, INITIAL_LEVELS, initialHistory } from './data';

/** In-memory store behind the MSW handlers. `reset()` gives each test a clean copy. */
export const db = {
  jobs: [] as JobDto[],
  levels: {} as Record<string, Level>,
  history: {} as Record<string, StatusEventDto[]>,
  reset(now: Date = new Date()) {
    this.levels = { ...INITIAL_LEVELS };
    this.jobs = buildJobs(now, this.levels);
    this.history = Object.fromEntries(this.jobs.map((j) => [j.application.id, initialHistory(j)]));
  },
};
db.reset();
