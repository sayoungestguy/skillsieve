import { api, toQuery } from './client';
import type {
  AccessTokenDto,
  ApplicationDto,
  CreatedTokenDto,
  CreateJobRequest,
  InsightSummaryDto,
  JobDto,
  JobSummaryDto,
  Level,
  LoginRequest,
  MeDto,
  Page,
  SearchHitDto,
  SkillDto,
  SkillInsightDto,
  Status,
  StatusEventDto,
  UpdateApplicationRequest,
  UserSkillDto,
} from './types';

const json = (body: unknown) => JSON.stringify(body);

export const authApi = {
  login: (body: LoginRequest) =>
    api<undefined>('/api/auth/login', { method: 'POST', body: json(body) }),
  logout: () => api<undefined>('/api/auth/logout', { method: 'POST' }),
  me: () => api<MeDto>('/api/me'),
};

export const jobsApi = {
  list: (params: { status?: Status; q?: string } = {}) =>
    api<Page<JobSummaryDto>>(`/api/jobs${toQuery({ ...params, page: 0, size: 200 })}`),
  get: (id: string) => api<JobDto>(`/api/jobs/${id}`),
  create: (body: CreateJobRequest) =>
    api<JobDto>('/api/jobs', { method: 'POST', body: json(body) }),
  retryExtraction: (id: string) => api<undefined>(`/api/jobs/${id}/extract`, { method: 'POST' }),
};

export const applicationsApi = {
  update: (id: string, body: UpdateApplicationRequest) =>
    api<ApplicationDto>(`/api/applications/${id}`, { method: 'PATCH', body: json(body) }),
  history: (id: string) => api<StatusEventDto[]>(`/api/applications/${id}/history`),
};

export const skillsApi = {
  list: (q?: string) => api<SkillDto[]>(`/api/skills${toQuery({ q })}`),
  setLevel: (slug: string, level: Level) =>
    api<UserSkillDto>(`/api/me/skills/${slug}`, { method: 'PUT', body: json({ level }) }),
};

export const insightsApi = {
  skills: (params: { status?: Status; from?: string }) =>
    api<SkillInsightDto[]>(`/api/insights/skills${toQuery({ ...params, limit: 20 })}`),
  summary: () => api<InsightSummaryDto>('/api/insights/summary'),
};

export const searchApi = {
  search: (q: string) => api<Page<SearchHitDto>>(`/api/search${toQuery({ q, page: 0, size: 20 })}`),
};

export const tokensApi = {
  list: () => api<AccessTokenDto[]>('/api/tokens'),
  create: (name: string) =>
    api<CreatedTokenDto>('/api/tokens', { method: 'POST', body: json({ name }) }),
  revoke: (id: string) => api<undefined>(`/api/tokens/${id}`, { method: 'DELETE' }),
};
