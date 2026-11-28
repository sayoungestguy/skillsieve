import { http, HttpResponse } from 'msw';
import type {
  CreateJobRequest,
  JobDto,
  JobSummaryDto,
  Level,
  SearchHitDto,
  SkillInsightDto,
  TailoringDto,
  UpdateApplicationRequest,
} from '@/api/types';
import { isGap } from '@/lib/levels';
import { SKILLS } from './data';
import { db, MOCK_USER, MOCK_WRONG_PASSWORD } from './db';

const MIN_TEXT = 200;
const TOP_SKILLS = 3;

const withLevels = (job: JobDto): JobDto => ({
  ...job,
  skills: job.skills.map((s) => ({ ...s, myLevel: db.levels[s.slug] ?? 0 })),
});

const toSummary = (j: JobDto): JobSummaryDto => ({
  id: j.id,
  company: j.company,
  title: j.title,
  extractionStatus: j.extractionStatus,
  topSkills: j.skills
    .filter((s) => s.importance === 'REQUIRED')
    .slice(0, TOP_SKILLS)
    .map((s) => s.name),
  applicationId: j.application.id,
  status: j.application.status,
  version: j.application.version,
  nextActionAt: j.application.nextActionAt,
  updatedAt: j.application.appliedAt ?? j.createdAt,
});

const page = <T>(content: T[]) => ({
  content,
  totalElements: content.length,
  page: 0,
  size: content.length,
});
const pct = (n: number) => Math.round((n / Math.max(db.jobs.length, 1)) * 100);
const notFound = () => HttpResponse.json({ title: 'Not found' }, { status: 404 });

function skillFrequency(slug: string) {
  const req = db.jobs.filter((j) =>
    j.skills.some((s) => s.slug === slug && s.importance === 'REQUIRED'),
  ).length;
  const pref = db.jobs.filter((j) =>
    j.skills.some((s) => s.slug === slug && s.importance === 'PREFERRED'),
  ).length;
  return { requiredPct: pct(req), preferredPct: pct(pref), totalPct: pct(req + pref) };
}

const unauthorized = () =>
  HttpResponse.json({ title: 'Unauthorized', detail: 'Sign in first.' }, { status: 401 });

export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    const { password } = (await request.json()) as { password: string };
    if (password === MOCK_WRONG_PASSWORD)
      return HttpResponse.json({ title: 'Login failed' }, { status: 401 });
    db.session = MOCK_USER;
    return new HttpResponse(null, { status: 204 });
  }),

  http.post('/api/auth/logout', () => {
    db.session = null;
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('/api/me', () => (db.session ? HttpResponse.json(db.session) : unauthorized())),

  http.get('/api/jobs', () => HttpResponse.json(page(db.jobs.map(toSummary)))),

  http.get('/api/jobs/:id', ({ params }) => {
    const job = db.jobs.find((j) => j.id === params.id);
    return job ? HttpResponse.json(withLevels(job)) : notFound();
  }),

  http.post('/api/jobs', async ({ request }) => {
    const body = (await request.json()) as CreateJobRequest;
    if (body.rawText.trim().length < MIN_TEXT) {
      return HttpResponse.json(
        { title: 'Validation failed', detail: 'Paste at least 200 characters.' },
        { status: 422 },
      );
    }
    const dup = db.jobs.find(
      (j) => j.rawText.trim().toLowerCase() === body.rawText.trim().toLowerCase(),
    );
    if (dup) return HttpResponse.json({ title: 'Duplicate', existingId: dup.id }, { status: 409 });
    const id = `j-${db.jobs.length + 1}-${Date.now()}`;
    const job: JobDto = {
      id,
      company: null,
      title: null,
      location: null,
      sourceUrl: body.sourceUrl ?? null,
      rawText: body.rawText,
      seniority: null,
      minYears: null,
      extractionStatus: 'PENDING',
      extractionError: null,
      skills: [],
      createdAt: new Date().toISOString(),
      application: {
        id: `a-${id}`,
        status: 'SAVED',
        appliedAt: null,
        nextActionAt: null,
        notes: null,
        version: 0,
      },
    };
    db.jobs.unshift(job);
    db.history[job.application.id] = [
      { fromStatus: null, toStatus: 'SAVED', changedAt: job.createdAt },
    ];
    return HttpResponse.json(job, { status: 201 });
  }),

  http.post('/api/jobs/:id/extract', () => new HttpResponse(null, { status: 202 })),

  http.get('/api/resume', () => (db.resume ? HttpResponse.json(db.resume) : notFound())),
  http.put('/api/resume', async ({ request }) => {
    const { text } = (await request.json()) as { text: string };
    const clean = text.trim();
    db.resume = { text: clean, charCount: clean.length, updatedAt: new Date().toISOString() };
    return HttpResponse.json(db.resume);
  }),
  http.delete('/api/resume', () => {
    db.resume = null;
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('/api/jobs/:id/tailoring', ({ params }) => {
    const t = db.tailoring[params.id as string];
    if (!t) return notFound();
    // Finish on the next read, like the background run.
    if (t.status === 'PENDING') {
      db.tailoring[params.id as string] = {
        ...t,
        status: 'DONE',
        model: 'mock-local',
        finishedAt: new Date().toISOString(),
        suggestions: [
          {
            original: db.resume?.text.split('\n')[0] ?? null,
            suggested: 'Designed and shipped REST APIs in Java used by 3 internal teams',
            skills: ['Java'],
            reason: "Uses the post's wording for a skill you already show.",
          },
        ],
      };
    }
    return HttpResponse.json(db.tailoring[params.id as string]);
  }),
  http.post('/api/jobs/:id/tailoring', ({ params }) => {
    if (!db.resume) {
      return HttpResponse.json(
        { title: 'Cannot suggest yet', detail: 'Save your resume in Settings first.' },
        { status: 409 },
      );
    }
    const t: TailoringDto = {
      status: 'PENDING',
      stale: false,
      model: null,
      suggestions: [],
      error: null,
      requestedAt: new Date().toISOString(),
      finishedAt: null,
    };
    db.tailoring[params.id as string] = t;
    return HttpResponse.json(t, { status: 202 });
  }),

  http.get('/api/tokens', () => HttpResponse.json(db.tokens)),
  http.post('/api/tokens', async ({ request }) => {
    const { name } = (await request.json()) as { name: string };
    const token = `ssv_mock${crypto.randomUUID().replaceAll('-', '')}`;
    const info = {
      id: crypto.randomUUID(),
      name,
      prefix: token.slice(0, 12),
      createdAt: new Date().toISOString(),
      lastUsedAt: null,
    };
    db.tokens.unshift(info);
    return HttpResponse.json({ info, token }, { status: 201 });
  }),
  http.delete('/api/tokens/:id', ({ params }) => {
    db.tokens = db.tokens.filter((t) => t.id !== params.id);
    return new HttpResponse(null, { status: 204 });
  }),

  http.patch('/api/applications/:id', async ({ params, request }) => {
    const body = (await request.json()) as UpdateApplicationRequest;
    const job = db.jobs.find((j) => j.application.id === params.id);
    if (!job) return notFound();
    const app = job.application;
    if (body.version !== app.version)
      return HttpResponse.json({ title: 'Stale version' }, { status: 409 });
    if (body.status && body.status !== app.status) {
      db.history[app.id]?.push({
        fromStatus: app.status,
        toStatus: body.status,
        changedAt: new Date().toISOString(),
      });
    }
    Object.assign(app, {
      ...(body.status !== undefined && { status: body.status }),
      ...(body.nextActionAt !== undefined && { nextActionAt: body.nextActionAt }),
      ...(body.notes !== undefined && { notes: body.notes }),
      version: app.version + 1,
    });
    return HttpResponse.json(app);
  }),

  http.get('/api/applications/:id/history', ({ params }) =>
    HttpResponse.json([...(db.history[String(params.id)] ?? [])].reverse()),
  ),

  http.get('/api/skills', ({ request }) => {
    const q = (new URL(request.url).searchParams.get('q') ?? '').toLowerCase();
    return HttpResponse.json(
      SKILLS.filter((s) => s.name.toLowerCase().includes(q)).map((s) => ({
        ...s,
        frequencyPct: skillFrequency(s.slug).totalPct,
        myLevel: db.levels[s.slug] ?? 0,
      })),
    );
  }),

  http.put('/api/me/skills/:slug', async ({ params, request }) => {
    const { level } = (await request.json()) as { level: Level };
    db.levels[String(params.slug)] = level;
    return HttpResponse.json({ slug: params.slug, level });
  }),

  http.get('/api/insights/skills', () => {
    const rows: SkillInsightDto[] = SKILLS.map((s) => {
      const f = skillFrequency(s.slug);
      const myLevel = db.levels[s.slug] ?? 0;
      return { ...s, ...f, myLevel, isGap: isGap(f.totalPct, myLevel) };
    })
      .filter((r) => r.totalPct > 0)
      .sort((a, b) => b.totalPct - a.totalPct)
      .slice(0, 12);
    return HttpResponse.json(rows);
  }),

  http.get('/api/insights/summary', () => {
    const total = db.jobs.length;
    const applied = db.jobs.filter((j) => j.application.status !== 'SAVED').length;
    const interviews = db.jobs.filter((j) =>
      ['INTERVIEW', 'OFFER'].includes(j.application.status),
    ).length;
    const offers = db.jobs.filter((j) => j.application.status === 'OFFER').length;
    return HttpResponse.json({
      total,
      applied,
      interviews,
      offers,
      interviewRate: applied ? interviews / applied : 0,
    });
  }),

  http.get('/api/search', ({ request }) => {
    const q = (new URL(request.url).searchParams.get('q') ?? '').trim();
    const ql = q.toLowerCase();
    const hits: SearchHitDto[] = !ql
      ? []
      : db.jobs
          .filter((j) => `${j.company} ${j.title} ${j.rawText}`.toLowerCase().includes(ql))
          .map((j) => {
            const i = j.rawText.toLowerCase().indexOf(ql);
            return {
              jobId: j.id,
              company: j.company,
              title: j.title,
              status: j.application.status,
              snippet:
                i < 0
                  ? { before: j.rawText.slice(0, 140), match: '', after: '' }
                  : {
                      before: j.rawText.slice(Math.max(0, i - 60), i),
                      match: j.rawText.slice(i, i + q.length),
                      after: j.rawText.slice(i + q.length, i + q.length + 80),
                    },
            };
          });
    return HttpResponse.json(page(hits));
  }),
];
