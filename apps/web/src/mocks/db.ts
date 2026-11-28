import type {
  AccessTokenDto,
  AuthConfigDto,
  ResumeDto,
  TailoringDto,
  JobDto,
  Level,
  MeDto,
  StatusEventDto,
} from '@/api/types';
import { buildJobs, INITIAL_LEVELS, initialHistory } from './data';

/** In-memory store behind the MSW handlers. `reset()` gives each test a clean copy. */
export const MOCK_USER: MeDto = {
  id: 'u-owner',
  email: 'demo@skillsieve.dev',
  role: 'OWNER',
  viaKeycloak: false,
};
/** Password the mock login rejects, so tests can exercise the error path. */
export const MOCK_WRONG_PASSWORD = 'wrong-password';

export const db = {
  /** Mock mode starts signed in, so the demo build opens straight on the board. */
  session: MOCK_USER as MeDto | null,
  jobs: [] as JobDto[],
  levels: {} as Record<string, Level>,
  history: {} as Record<string, StatusEventDto[]>,
  tokens: [] as AccessTokenDto[],
  authConfig: { oidcEnabled: false, oidcLogoutUrl: null } as AuthConfigDto,
  resume: null as ResumeDto | null,
  tailoring: {} as Record<string, TailoringDto>,
  reset(now: Date = new Date()) {
    this.tokens = [];
    this.authConfig = { oidcEnabled: false, oidcLogoutUrl: null };
    this.resume = null;
    this.tailoring = {};
    this.session = MOCK_USER;
    this.levels = { ...INITIAL_LEVELS };
    this.jobs = buildJobs(now, this.levels);
    this.history = Object.fromEntries(this.jobs.map((j) => [j.application.id, initialHistory(j)]));
  },
};
db.reset();
