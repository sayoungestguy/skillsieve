/**
 * API types from docs/TECHNICAL_DESIGN.md §5.
 * Hand-written until core-api publishes openapi.json; then replace with `npm run gen:api` output.
 */
export type Status = 'SAVED' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN';
export type Level = 0 | 1 | 2 | 3;
export type ExtractionStatus = 'PENDING' | 'DONE' | 'FAILED';
export type Importance = 'REQUIRED' | 'PREFERRED';
export type Role = 'OWNER' | 'DEMO' | 'USER';

export interface MeDto {
  id: string;
  email: string;
  role: Role;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface JobSkill {
  slug: string;
  name: string;
  importance: Importance;
  myLevel: Level;
}

export interface ApplicationDto {
  id: string;
  status: Status;
  appliedAt: string | null;
  nextActionAt: string | null;
  notes: string | null;
  version: number;
}

export interface JobDto {
  id: string;
  company: string | null;
  title: string | null;
  location: string | null;
  sourceUrl: string | null;
  rawText: string;
  seniority: string | null;
  minYears: number | null;
  extractionStatus: ExtractionStatus;
  /** Set when extraction could not finish; with PENDING it means "try again later". */
  extractionError: string | null;
  skills: JobSkill[];
  application: ApplicationDto;
  createdAt: string;
}

/** Board card. */
export interface JobSummaryDto {
  id: string;
  company: string | null;
  title: string | null;
  extractionStatus: ExtractionStatus;
  topSkills: string[];
  applicationId: string;
  status: Status;
  version: number;
  nextActionAt: string | null;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
}

export interface StatusEventDto {
  fromStatus: Status | null;
  toStatus: Status;
  changedAt: string;
}

export interface SkillDto {
  slug: string;
  name: string;
  category: string;
  frequencyPct: number;
  myLevel: Level;
}

export interface UserSkillDto {
  slug: string;
  level: Level;
}

export interface SkillInsightDto {
  slug: string;
  name: string;
  category: string;
  requiredPct: number;
  preferredPct: number;
  totalPct: number;
  myLevel: Level;
  isGap: boolean;
}

export interface InsightSummaryDto {
  total: number;
  applied: number;
  interviews: number;
  offers: number;
  interviewRate: number;
}

export interface SearchHitDto {
  jobId: string;
  company: string | null;
  title: string | null;
  status: Status;
  /** Snippet split around the match so the UI never renders HTML from the server. */
  snippet: { before: string; match: string; after: string };
}

export interface CreateJobRequest {
  rawText: string;
  sourceUrl?: string;
}

export interface UpdateApplicationRequest {
  status?: Status;
  nextActionAt?: string | null;
  notes?: string | null;
  position?: number;
  version: number;
}
