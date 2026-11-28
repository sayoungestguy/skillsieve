# Technical Design Document — SkillSieve

> Version 0.1 · Last updated 2026-11-22 23:58 SGT · Status: Draft
> Read with: [ARCHITECTURE.md](ARCHITECTURE.md) (big picture) and [PRD.md](PRD.md) (scope).

## 1. Scope
This document defines the data model, APIs, LLM contract, configuration, security, testing, CI/CD and operations for Release 1, plus the planned changes for Release 2.

## 2. Repository layout

```
apps/web/
  src/{api,components,features/{board,jobs,insights,skills,search},hooks,lib,routes,styles}
services/core-api/
  src/main/java/dev/skillsieve/core/
    auth/  job/  application/  skill/  insight/  search/  common/  config/
  src/main/resources/{application.yml, db/migration/}
services/skills-svc/
  app/{main.py, api/, extraction/, llm/, taxonomy/, cache/, config.py}
  tests/
data/taxonomy/skills.yaml
deploy/{helm/skillsieve, k3s, local}
.github/workflows/{ci.yml, deploy.yml, keepalive.yml, codeql.yml}
```

Packages are **by feature**, not by layer (each feature has its controller, service, repository, DTOs).

## 3. Data model (Postgres, schema `skillsieve`)

**Migration ownership:** core-api runs **all** Flyway migrations (`V1__init.sql`, …). skills-svc only reads `skill`/`skill_alias`, reads/writes `extraction_cache`, and (R2 worker) reads `job_post.raw_text`/`text_hash` by id.

```sql
CREATE SCHEMA IF NOT EXISTS skillsieve;
SET search_path TO skillsieve;

CREATE TABLE app_user (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,              -- BCrypt (cost 12)
  role          TEXT NOT NULL DEFAULT 'OWNER' CHECK (role IN ('OWNER','DEMO','USER')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE job_post (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  company           TEXT,
  title             TEXT,
  location          TEXT,
  source_url        TEXT,
  raw_text          TEXT NOT NULL,
  text_hash         CHAR(64) NOT NULL,       -- SHA-256 hex of normalised text
  seniority         TEXT,                    -- from LLM: JUNIOR|MID|SENIOR|LEAD|UNKNOWN
  min_years         SMALLINT,
  extraction_status TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (extraction_status IN ('PENDING','DONE','FAILED')),
  extraction_error  TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, text_hash)
);

CREATE TABLE application (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_post_id     UUID NOT NULL UNIQUE REFERENCES job_post(id) ON DELETE CASCADE,
  status          TEXT NOT NULL DEFAULT 'SAVED'
                  CHECK (status IN ('SAVED','APPLIED','INTERVIEW','OFFER','REJECTED','WITHDRAWN')),
  applied_at      TIMESTAMPTZ,
  next_action_at  DATE,
  notes           TEXT,
  position        INT NOT NULL DEFAULT 0,  -- order inside a board column
  version         INT NOT NULL DEFAULT 0,  -- optimistic locking
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE status_event (
  id             BIGSERIAL PRIMARY KEY,
  application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
  from_status    TEXT,
  to_status      TEXT NOT NULL,
  changed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE skill (
  id        SERIAL PRIMARY KEY,
  slug      TEXT NOT NULL UNIQUE,            -- 'kubernetes'
  name      TEXT NOT NULL,                   -- 'Kubernetes'
  category  TEXT NOT NULL                    -- LANGUAGE|FRAMEWORK|DATABASE|CLOUD|DEVOPS|AI|PRACTICE|SOFT|OTHER
);

CREATE TABLE skill_alias (
  alias     TEXT PRIMARY KEY,                -- lower-case: 'k8s'
  skill_id  INT NOT NULL REFERENCES skill(id) ON DELETE CASCADE
);

CREATE TABLE job_skill (
  job_post_id UUID NOT NULL REFERENCES job_post(id) ON DELETE CASCADE,
  skill_id    INT  NOT NULL REFERENCES skill(id),
  importance  TEXT NOT NULL CHECK (importance IN ('REQUIRED','PREFERRED')),
  evidence    TEXT,                          -- short phrase from the post
  PRIMARY KEY (job_post_id, skill_id)
);

CREATE TABLE user_skill (
  user_id   UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  skill_id  INT  NOT NULL REFERENCES skill(id),
  level     SMALLINT NOT NULL CHECK (level BETWEEN 0 AND 3),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, skill_id)
);

CREATE TABLE extraction_cache (
  text_hash     CHAR(64) PRIMARY KEY,
  model         TEXT NOT NULL,
  prompt_version TEXT NOT NULL,
  result_json   JSONB NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- R2 (V4__access_token.sql): Chrome extension tokens; only SHA-256 of the token is stored.
CREATE TABLE access_token (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  token_hash    CHAR(64) NOT NULL UNIQUE,
  prefix        TEXT NOT NULL,             -- ssv_ + 8 chars, shown in Settings
  created_at    TIMESTAMPTZ NOT NULL,
  last_used_at  TIMESTAMPTZ,
  revoked_at    TIMESTAMPTZ
);

-- R2 (V5__resume.sql): one resume per user, encrypted by core-api (AES-256-GCM, RESUME_KEY).
CREATE TABLE resume (
  user_id       UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  ciphertext    BYTEA NOT NULL,            -- version (1) | nonce (12) | ciphertext + tag; AAD = user id
  content_hmac  CHAR(64) NOT NULL,         -- HMAC-SHA256 of the text (derived key): cache key for tailoring
  char_count    INTEGER NOT NULL,
  updated_at    TIMESTAMPTZ NOT NULL
);

-- R2 (V2__outbox.sql): transactional outbox, drained to RabbitMQ by OutboxPublisher (§14).
CREATE TABLE outbox (
  id            UUID PRIMARY KEY,          -- = message eventId
  event_type    TEXT NOT NULL,             -- routing key, e.g. job.created
  aggregate_id  UUID NOT NULL,             -- job_post.id
  payload       JSONB NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL,
  sent_at       TIMESTAMPTZ,               -- NULL = not yet confirmed by the broker
  attempts      INT NOT NULL DEFAULT 0,
  last_error    TEXT
);

CREATE INDEX idx_job_post_user_created ON job_post(user_id, created_at DESC);
CREATE INDEX idx_application_status    ON application(status);
CREATE INDEX idx_status_event_app      ON status_event(application_id, changed_at);
```

**Row Level Security:** enabled on every table (`V1__init.sql`) with **no policies**. core-api connects as a dedicated role `skillsieve_api` (not `postgres`, not the Supabase `anon` key). That role runs the migrations, so it owns the tables and bypasses RLS. Every other role, including Supabase's `anon` and `authenticated`, sees nothing. Ownership checks (`user_id = :currentUser`) are enforced in the service layer and covered by `CrossUserAccessIT`. Each connection runs `SET search_path TO skillsieve` (Hikari `connection-init-sql`), so raw SQL resolves our tables. Setup steps: `docs/runbooks/supabase.md`.

**Skill taxonomy seed:** `data/taxonomy/skills.yaml` is the source (slug, name, category, aliases). `scripts/gen_skill_seed.py` turns it into the repeatable migration `R__seed_skills.sql` (upsert, so re-running is safe). Unmapped skills from extraction are reviewed weekly and added to the YAML.

**Text normalisation** (for `text_hash`): Unicode NFKC → lower-case → collapse all whitespace to one space → trim. Implemented identically in Java and Python, with a shared test vector file `data/test-vectors/normalise.json`.

**Size estimate:** 1,000 posts × ~6 KB = ~6 MB. Far below the 500 MB cap.

## 4. Elasticsearch

Index alias `job-posts-current` points at a concrete index `job-posts-v1-<UTC timestamp>`. A reindex builds a new one and swaps the alias atomically, with no downtime. The mapping lives in `services/core-api/src/main/resources/es/job-posts-mapping.json` (strict). Documents hold skill **slugs**.

```json
{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0,
    "analysis": { "analyzer": { "en_std": { "type": "standard", "stopwords": "_english_" } } } },
  "mappings": {
    "dynamic": "strict",
    "properties": {
      "jobId":     { "type": "keyword" },
      "userId":    { "type": "keyword" },
      "company":   { "type": "text", "fields": { "raw": { "type": "keyword" } } },
      "title":     { "type": "text", "fields": { "raw": { "type": "keyword" } } },
      "body":      { "type": "text", "analyzer": "en_std" },
      "status":    { "type": "keyword" },
      "seniority": { "type": "keyword" },
      "skills": { "properties": {
        "required":  { "type": "keyword" },
        "preferred": { "type": "keyword" },
        "all":       { "type": "keyword" } } },
      "createdAt": { "type": "date" }
    }
  }
}
```

Gap query (simplified):
```json
{ "size": 0,
  "query": { "bool": { "filter": [
    { "term": { "userId": "<uid>" } },
    { "terms": { "status": ["APPLIED","INTERVIEW"] } },
    { "range": { "createdAt": { "gte": "now-90d" } } } ] } },
  "aggs": {
    "required":  { "terms": { "field": "skills.required",  "size": 30 } },
    "preferred": { "terms": { "field": "skills.preferred", "size": 30 } } } }
```
`frequencyPct = bucket.doc_count / hits.total × 100`.

**Availability and fallback:** core-api checks for the alias at startup and every 15 s until it exists, building the index from Postgres when it's missing. Until the index is ready, nothing is written to ES: a write to the bare alias name would make ES auto-create an unmapped index. Search (multi_match on company^3, title^3, body, skills.all^2, with a body highlight split into before/match/after) and the skill-gap aggregation use ES when it's ready. If ES is off (`ES_URL` empty) or a call fails, they answer from Postgres (`ILIKE` / SQL aggregation) with the same API shape. The summary card always comes from Postgres.

**Sync rule:** core-api re-indexes a job document after every change to the job, its status, or its skills (in the same service method, after the DB transaction commits — `@TransactionalEventListener(AFTER_COMMIT)`). If ES is down, the event goes to an `es_outbox` table (R2) / is logged and fixed by `POST /api/admin/reindex` (R1). The R1 implementation is `JobChangedEvent`, published by job, application and extraction changes and handled by `JobIndexListener` with `refresh=wait_for`, so a change is searchable when the request returns.

## 5. API contract — core-api (`/api`, JSON)

All errors use **RFC 7807 Problem Details** (`application/problem+json`):
`{ "type", "title", "status", "detail", "instance", "traceId", "errors": [{field, message}] }`

| Method | Path | Body / query | Response | Notes |
|---|---|---|---|---|
| POST | `/api/auth/login` | `{email, password}` | 204 + `Set-Cookie: ss_token` | Rate limit 5/min/IP |
| POST | `/api/auth/logout` | — | 204 | Clears cookie |
| GET | `/api/me` | — | `{id, email, role}` | |
| POST | `/api/jobs` | `{rawText, sourceUrl?}` | 201 `JobDto` · 409 duplicate `{existingId}` · 422 validation | `rawText` 200–30,000 chars |
| GET | `/api/jobs` | `status?, q?, page=0, size=50` | `Page<JobSummaryDto>` | Board data |
| GET | `/api/jobs/{id}` | — | `JobDto` | Includes skills + application |
| PATCH | `/api/jobs/{id}` | `{company?, title?, location?, sourceUrl?}` | `JobDto` | |
| DELETE | `/api/jobs/{id}` | — | 204 | Also deletes ES doc |
| POST | `/api/jobs/{id}/extract` | — | 202 | Retry extraction |
| PATCH | `/api/applications/{id}` | `{status?, nextActionAt?, notes?, position?, version}` | `ApplicationDto` · 409 on stale `version` | Writes `status_event` on status change |
| GET | `/api/applications/{id}/history` | — | `StatusEventDto[]` | |
| GET | `/api/skills` | `q?, category?` | `SkillDto[]` | Taxonomy + my level |
| PUT | `/api/me/skills/{slug}` | `{level: 0..3}` | `UserSkillDto` | |
| GET | `/api/insights/skills` | `status?, from?, to?, limit=20` | `SkillInsightDto[]` | See §4 |
| GET | `/api/insights/summary` | — | `{total, applied, interviews, offers, interviewRate}` | |
| GET | `/api/search` | `q, page, size` | `Page<SearchHitDto>` | Highlighted snippets |
| GET | `/api/tokens` | — | `AccessTokenDto[]` `{id, name, prefix, createdAt, lastUsedAt}` | Active extension tokens (never the secret) |
| POST | `/api/tokens` | `{name}` (≤ 60) | 201 `{info: AccessTokenDto, token}` · 409 at 10 active | `token` (`ssv_…`) is shown once; cookie session only, OWNER/USER |
| DELETE | `/api/tokens/{id}` | — | 204 · 404 if not mine | Revoke |
| GET | `/api/resume` | — | `ResumeDto` `{text, charCount, updatedAt}` · 404 if none · 503 if `RESUME_KEY` unset | My resume, decrypted; `Cache-Control: no-store`; extension tokens get 403 |
| PUT | `/api/resume` | `{text}` (1–20,000 chars, trimmed) | 200 `ResumeDto` · 422 | Create or replace |
| DELETE | `/api/resume` | — | 204 | Delete my resume |
| POST | `/api/admin/reindex` | — | 202 `{indexed}` · 409 when ES is off | OWNER only |

Key DTOs (Java `record`s, TypeScript types generated from OpenAPI with `openapi-typescript`):

```ts
type Status = 'SAVED'|'APPLIED'|'INTERVIEW'|'OFFER'|'REJECTED'|'WITHDRAWN';
interface JobDto {
  id: string; company: string|null; title: string|null; location: string|null;
  sourceUrl: string|null; rawText: string; seniority: string|null; minYears: number|null;
  extractionStatus: 'PENDING'|'DONE'|'FAILED';
  skills: { slug: string; name: string; importance: 'REQUIRED'|'PREFERRED'; myLevel: 0|1|2|3 }[];
  application: { id: string; status: Status; appliedAt: string|null; nextActionAt: string|null;
                 notes: string|null; version: number };
  createdAt: string;
}
interface SkillInsightDto {
  slug: string; name: string; category: string;
  requiredPct: number; preferredPct: number; totalPct: number;
  myLevel: 0|1|2|3; isGap: boolean;   // totalPct >= 30 && myLevel <= 1
}
interface JobSummaryDto {             // board card (GET /api/jobs)
  id: string; company: string|null; title: string|null;
  extractionStatus: 'PENDING'|'DONE'|'FAILED';
  topSkills: string[];                // up to 3 required skill names
  applicationId: string; status: Status; version: number;
  nextActionAt: string|null; updatedAt: string;
}
interface SkillDto {                  // GET /api/skills
  slug: string; name: string; category: string; frequencyPct: number; myLevel: 0|1|2|3;
}
interface SearchHitDto {              // GET /api/search
  jobId: string; company: string|null; title: string|null; status: Status;
  snippet: { before: string; match: string; after: string };  // plain text; the web app wraps `match` in <mark>
}
```

Web auth: `GET /api/me` decides whether the app is signed in (401 → `/login`). The query client treats any later 401 as "session ended". Category values are shown through `formatCategory` (`DEVOPS` → "DevOps").

Until springdoc output exists, the web app hand-writes these types in `apps/web/src/api/types.ts`.

**Implementation notes (core-api):** auth is Spring Security's OAuth2 resource server (Nimbus, HS256) reading the JWT from the `ss_token` cookie (`HttpOnly; Secure; SameSite=Strict; Path=/api`). The `DEMO` role may only `GET`. `PATCH /api/applications/{id}` treats a field sent as `null` (e.g. `nextActionAt`) as "clear" and a missing field as "leave alone". The login rate limit is in memory, keyed by client IP (`server.forward-headers-strategy=native` behind Traefik). Request bodies are capped at 64 KB.

OpenAPI spec is generated by springdoc at `/v3/api-docs` and committed to `services/core-api/openapi.json` in CI (diff check fails the build if it changes without a commit).

## 6. API contract — skills-svc (internal only)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/v1/extract` | `{textHash, text}` | `ExtractionResult` |
| POST | `/v1/tailor` | `TailorRequest` `{resumeText (≤ 20,000), jobTitle?, company?, skills: [{name, importance}] (1–60), missingSkills?}` | `TailorResult` `{model, promptVersion, suggestions: [{original, suggested, skills, reason}]}` (R2, FR-12) |
| GET | `/healthz` | — | `{status:"ok"}` |
| GET | `/readyz` | — | 200 when taxonomy loaded + DB reachable |

Header `X-Internal-Token` required (shared secret, compared in constant time). Not exposed by ingress. JSON is **camelCase** on the wire (`textHash`, `minYears`, `promptVersion`), the same as core-api; the Python models use snake_case with an alias generator.

Errors: `401` bad token · `422` bad body · `429` LLM budget or Gemini rate limit (core-api keeps the job `PENDING`) · `503` no `GEMINI_API_KEY`, Gemini down, or output invalid twice (also `PENDING`). Any other error makes core-api mark the job `FAILED`.

**Resume tailoring (`/v1/tailor`, R2 FR-12 part 2):** answered by a **self-hosted Ollama model only** (`OLLAMA_URL`); resume text never reaches Gemini (CLAUDE.md C5). Guards: the `app/tailor` package never imports the Gemini client (a test parses its imports), and `OLLAMA_URL` is refused at startup if it points at a hosted LLM API (Google, OpenAI, Anthropic). Only the resume's **list items** are sent (or, with none, lines ≥ 25 chars), numbered, so the name and contact lines at the top never reach the model. The model (prompt `tailor-v2`, Ollama JSON-schema output, temperature 0.2) picks bullets **by number** and rewrites them; skills-svc fills in `original` itself, then drops a rewrite that is unchanged, repeats a bullet, has a bad number, or **mentions a job skill the resume does not show** (the gap list plus any job skill absent from the resume). `skills` keeps only job skills the rewrite mentions. At most 5 suggestions. If every rewrite is dropped it asks once more, then returns 503. Errors: `422` no bullet lines · `503` `OLLAMA_URL` unset, Ollama down, invalid JSON twice, or nothing usable. Logs and metrics hold counts and timings only (`skillsieve_tailor_calls{result=ok|error|rejected}`, `skillsieve_tailor_duration_seconds`). On k3d with `qwen2.5:1.5b` a warm answer takes ~60–75 s (2 ARM-class cores), so core-api must allow ~4 min in part 3.

**Taxonomy refresh:** skills-svc reloads `skill_alias` every `TAXONOMY_REFRESH_S` (300 s). core-api applies the seed with Flyway and may start after skills-svc, so new aliases arrive without a restart.

**Cache contents:** `extraction_cache.result_json` stores the **raw LLM output**, not the mapped result. Alias mapping runs again on every read, so adding an alias to `skills.yaml` fixes old posts without spending quota.

**core-api side (R1, ADR-006):** `POST /api/jobs` commits the job, then calls skills-svc *outside* the DB transaction (read timeout `EXTRACTION_TIMEOUT`, default 10 s), then stores the result in a short transaction. Company, title and location fill in only fields the user hasn't set. Saving never fails because of extraction. `JobDto.extractionError` explains a `PENDING` ("try later") or `FAILED` job, and the web app shows **Retry** instead of polling.

```python
class ExtractedSkill(BaseModel):
    slug: str                 # canonical, after alias mapping
    raw: str                  # as written in the post
    importance: Literal["REQUIRED", "PREFERRED"]
    evidence: str | None      # <= 120 chars

class ExtractionResult(BaseModel):
    company: str | None
    title: str | None
    location: str | None
    seniority: Literal["JUNIOR", "MID", "SENIOR", "LEAD", "UNKNOWN"]
    min_years: int | None
    skills: list[ExtractedSkill]
    unmapped: list[str]       # skills the taxonomy did not know (for review)
    model: str
    prompt_version: str
    cached: bool
```

## 7. LLM contract

- **Model:** Flash-Lite family. The default is `gemini-3.5-flash-lite`; override it with env `GEMINI_MODEL` and check AI Studio for current IDs and free limits.
- **SDK:** `google-genai`, `response_mime_type="application/json"`, `response_schema=<Pydantic schema>`, `temperature=0`.
- **Prompt version:** `extract-v1` (stored in `extraction_cache.prompt_version`; bump to invalidate cache).

System prompt (`app/llm/prompts/extract_v1.txt`):
```
You extract structured data from a software job post.
Return only JSON matching the schema.
Rules:
- "skills": technical skills, tools, platforms, practices. Skip pure soft skills unless named as a requirement (then category SOFT).
- importance = REQUIRED if listed under requirements/must-have/"you have"; PREFERRED if under nice-to-have/plus/bonus/preferred.
- Use the shortest common name ("Kubernetes", not "Kubernetes concepts and resources").
- Split combined items ("Docker/Podman" -> two skills).
- evidence: copy at most 12 words from the post that show the skill.
- If a field is not in the post, use null. Do not guess.
```

**Post-processing:**
1. Lower-case `raw`, strip punctuation → look up `skill_alias`. Hit → canonical `slug`. Miss → fuzzy match (RapidFuzz ratio ≥ 92) against aliases. Still miss → add to `unmapped`.
2. De-duplicate by `slug`; if a skill appears as both, keep `REQUIRED`.
3. Validate with Pydantic; on validation error, retry once with the error message appended; then fail.

**Quota protection:**
- Cache by `text_hash` + `prompt_version` (DB). Second save of the same text costs 0 calls.
- Token bucket in-process: `LLM_RPM` (default 4/min) and `LLM_RPD` (default from env). On limit → HTTP 429 to core-api → job stays `PENDING`.
- Retry on 429/5xx from Gemini: exponential backoff 2 s, 4 s, 8 s (max 3 tries), with jitter, all inside a 9 s budget (`LLM_BUDGET_S`) so core-api's 10 s timeout is never hit.
- Truncate post text to 12,000 characters before sending.

**Privacy guard:** a regex pass removes emails, phone numbers (`+65` and general), and URLs with query strings before the text leaves the cluster. Unit-tested.

**Evaluation:** `services/skills-svc/eval/` holds 20 labelled, original job posts (`*.txt` + `*.expected.json`). `uv run python -m eval.run` prints precision/recall per post. Target: precision ≥ 0.9, recall ≥ 0.85. Runs manually (it uses quota), not in CI. CI runs the same pipeline against recorded LLM responses (`eval/recorded/`, enforced by `tests/test_eval.py`). First recorded run (gemini-3.5-flash-lite): precision 1.000, recall 0.893. Details and caveats are in `eval/README.md`.

## 8. Configuration

| Var | Service | Example | Secret? |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | core-api | `jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require&currentSchema=skillsieve` | yes |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | core-api | `skillsieve_api` | yes |
| `JWT_SECRET` | core-api | 64 random bytes, base64 | yes |
| `OWNER_EMAIL` / `OWNER_PASSWORD_HASH` | core-api | seed owner on first start | yes |
| `ES_URL` | core-api | `http://<release>-elasticsearch:9200` (Helm sets it). Empty = Postgres fallback | no |
| `EXTRACTION_MODE` | core-api | `sync` (R1, default) or `async` (R2: outbox → RabbitMQ). Helm sets `async` when `rabbitmq.enabled` | no |
| `SPRING_RABBITMQ_HOST` | core-api | `<release>-rabbitmq` (Helm sets it); async mode only | no |
| `SPRING_RABBITMQ_USERNAME` / `_PASSWORD` | core-api | same pair as `RABBITMQ_DEFAULT_USER` / `_PASS` in `rabbitmq-secrets` | yes |
| `SKILLS_SVC_URL` | core-api | `http://skills-svc:8000` | no |
| `INTERNAL_TOKEN` | both | random 32 bytes | yes |
| `RESUME_KEY` | core-api | base64 of 32 random bytes (`openssl rand -base64 32`); AES-256-GCM key for resumes. Empty = resume storage off (503). **Never rotate without re-encrypting:** a new key makes stored resumes unreadable. | yes |
| `MANAGEMENT_PORT` | core-api | `8081` (actuator + Prometheus, in-cluster only) | no |
| `DEMO_MODE` | core-api | `true` → DEMO role read-only | no |
| `DATABASE_URL` | skills-svc | `postgresql://…?sslmode=require` | yes |
| `GEMINI_API_KEY` | skills-svc | from AI Studio | yes |
| `AMQP_URL` | skills-svc | `amqp://user:pass@<release>-rabbitmq:5672/` (R2). Empty = no queue worker, HTTP API only | yes |
| `GEMINI_MODEL` | skills-svc | `gemini-3.5-flash-lite` (default) | no |
| `OLLAMA_URL` | skills-svc | `http://<release>-ollama:11434` (set by Helm when `ollama.enabled`, or `ollama.externalUrl`). Empty = resume tailoring off (503). Hosted LLM APIs are refused. | no |
| `OLLAMA_MODEL` | skills-svc | `qwen2.5:1.5b` (default, Helm `ollama.model`) | no |
| `TAILOR_TIMEOUT_S` | skills-svc | `120` (one Ollama call) | no |
| `LLM_BUDGET_S` | skills-svc | `9` (whole extraction incl. retries) | no |
| `EXTRACTION_TIMEOUT` | core-api | `10s` | no |
| `LLM_RPM` / `LLM_RPD` | skills-svc | `4` / `200` | no |
| `PROMPT_VERSION` | skills-svc | `extract-v1` | no |
| `COOKIE_SECURE` | core-api | `true` (default). `false` only for plain-HTTP local/LAN clusters | no |
| `VITE_USE_MOCKS` | web (build-time) | `false` (default) → call core-api. `true` → serve fake data from MSW in the browser, already signed in, no backend needed | no |

Local: Spring profile `local` (`application-local.yml`) points at the compose Postgres on port 55432, generates a throwaway JWT secret and seeds `dev@skillsieve.local` with password `skillsieve-local`. These are dev-only values for a database bound to 127.0.0.1. For the local k3d cluster, `deploy/local/k3d-up.sh` creates the `core-api-secrets` Secret with random values and writes the owner login to the git-ignored `deploy/local/.owner-credentials`.
Cluster: ConfigMap for non-secrets, `SealedSecret` for secrets.

**Supabase connection:** use the **session pooler** (port 5432) for Flyway/JPA; HikariCP `maximumPoolSize=5` (free plan connection limits).

## 9. CI/CD and deployment

### 9.1 Workflows
| File | Trigger | Jobs |
|---|---|---|
| `ci.yml` | PR + push to `main` | Path-filtered: `web` (lint, typecheck, vitest, build) · `core-api` (Spotless, `mvn verify` with Testcontainers Postgres + ES) · `skills-svc` (ruff, mypy, pytest) · `helm` (lint, `kubeconform`) · `security` (Gitleaks, Trivy fs/config) |
| `deploy.yml` | push to `main` after CI passes | Build multi-arch images with `docker buildx` (`linux/arm64,linux/amd64`) → push to GHCR tagged `sha-<short>` + `main` → Trivy image scan (fail on fixable HIGH/CRITICAL) → SSH to VM → `helm upgrade --install --atomic --timeout 5m` with `image.tag=sha-<short>` → smoke test `curl -f https://<host>/healthz` (switches to `/api/actuator/health` once core-api ships). Chart is packaged as `<chart version>-sha<short>` and pushed to `oci://ghcr.io/<owner>/charts`; prod values are sent to the VM over stdin (`-f -`) |
| `keepalive.yml` | cron every 3 days | One `SELECT 1` against Supabase to avoid free-plan pause |
| `codeql.yml` | weekly + PR | CodeQL for Java, Python, JS/TS |

Dependabot: weekly for npm, Maven, pip (uv), GitHub Actions, Docker base images.

### 9.2 Helm chart `deploy/helm/skillsieve`
```
Chart.yaml
values.yaml            # defaults (local/k3d)
values-prod.yaml       # host, resources, replicas=1
templates/
  _helpers.tpl
  web-{deployment,service}.yaml
  core-api-{deployment,service,configmap}.yaml
  skills-svc-{deployment,service,configmap}.yaml
  elasticsearch-{statefulset,service,pvc}.yaml
  ingress.yaml          # Traefik, TLS via cert-manager annotation
  sealedsecret.yaml
  pg-backup-cronjob.yaml
  networkpolicy.yaml    # R2
```
Every Deployment: `resources`, `readinessProbe`, `livenessProbe`, `securityContext` (`runAsNonRoot: true`, `readOnlyRootFilesystem: true`, drop ALL capabilities), `automountServiceAccountToken: false`.

### 9.3 First-time VM setup (`deploy/k3s/bootstrap.sh`)
1. Ubuntu 24.04 ARM, update, enable unattended-upgrades, UFW: 22 (rate-limited, key-only, fail2ban), 80, 443. Traefik redirects HTTP → HTTPS.
2. Install k3s (`--write-kubeconfig-mode 600`, Traefik enabled).
3. Install Helm, cert-manager, sealed-secrets controller.
4. Create ClusterIssuer `letsencrypt-prod`.
5. Create namespace `skillsieve`, deploy key user for GitHub Actions (SSH forced command allows only `helm`/`kubectl`; kubeconfig uses ServiceAccount `deployer` bound to `admin` in `skillsieve` only).

Manual steps (VM creation, DuckDNS, GitHub secrets/vars, making GHCR packages public): `deploy/k3s/README.md`.
6. Add DuckDNS cron to keep the IP updated.

## 10. Security

| Area | Control |
|---|---|
| Transport | HTTPS only (HSTS), TLS to Supabase (`sslmode=require`) |
| Auth | BCrypt cost 12; JWT HS256, 1 h, HttpOnly + Secure + SameSite=Strict cookie; login rate limit |
| CSRF | SameSite=Strict + require `X-Requested-With` header on state-changing requests |
| Authorisation | Every query filters by `user_id`; tests for cross-user access return 404 |
| Input | Bean Validation / Pydantic; max body 64 KB; job text rendered as plain text (never `dangerouslySetInnerHTML`) |
| Headers | CSP `default-src 'self'` (temporary exception: `fonts.googleapis.com` / `fonts.gstatic.com` until Inter is self-hosted), `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy` (nginx + Spring) |
| Secrets | Sealed Secrets; Gitleaks in CI; no secrets in logs |
| Supply chain | Dependabot, Trivy, CodeQL, pinned base images by digest, non-root containers |
| LLM | PII redaction before send; no resume text to free tier; prompt-injection: output validated by schema, never executed |

## 11. Observability

| Release | What |
|---|---|
| R1 | Health probes (`/api/actuator/health/{liveness,readiness}`); JSON logs (Spring Boot structured logging, ECS format); Actuator `health` and `info` only. The `prometheus` endpoint moves to an in-cluster management port in R2; FastAPI `/metrics` via `prometheus-fastapi-instrumentator` |
| R2 part 1 ☑ | **Metrics.** core-api: Micrometer Prometheus registry on the **management port 8081** (`/actuator/prometheus`; no Service port, no Ingress). Kubelet probes use `/livez` and `/readyz` on the app port; smoke tests through the Ingress use `GET /api/health` (status only). skills-svc: `GET /metrics` (`prometheus-client`; no Ingress) plus an HTTP latency middleware labelled by route template. Helm (`observability.enabled`): Prometheus (namespace-scoped pod discovery via `prometheus.io/*` annotations, Role on pods only, 7 d / 1.5 GB retention) and Grafana at `/grafana` (admin login from `grafana-secrets`, no anonymous access, no sign-up, no phoning home; datasource and the *SkillSieve overview* dashboard provisioned from `deploy/helm/skillsieve/dashboards/`). |
| R2 part 2 ☑ | **Logs.** Loki 3.5 single binary (StatefulSet, filesystem TSDB, 2 Gi PVC, 7-day retention by the compactor, ClusterIP only, no auth). Grafana Alloy (one-replica Deployment) tails this release's pod logs **through the Kubernetes API** (`loki.source.kubernetes`; namespace Role on `pods` + `pods/log`, no hostPath, non-root) and pushes labels `namespace`, `component`, `pod`, `container` and a lowercased `level` parsed from the line. Grafana gets a `Loki` datasource and a **SkillSieve logs** dashboard (lines by level, errors by component, searchable log panel). `observability.loki.enabled` (default true when `observability.enabled`). |
| Later | OpenTelemetry traces (Tempo) and alerts (Telegram) once the RAM budget allows. |

Custom metrics (☑): core-api `skillsieve_jobs_saved_total`, `skillsieve_extractions_total{result=done|retry_later|failed}`; skills-svc `skillsieve_llm_calls_total{result=ok|error|quota}`, `skillsieve_llm_cache_hits_total`, `skillsieve_extraction_duration_seconds{cached}`, `skillsieve_llm_calls_today`, `skillsieve_llm_daily_limit`, `skillsieve_http_request_duration_seconds{method,route,status}`.

## 12. Testing strategy

| Level | Web | core-api | skills-svc |
|---|---|---|---|
| Unit | Vitest + React Testing Library | JUnit 5 + Mockito + AssertJ | pytest |
| Integration | MSW (mock API) | `@SpringBootTest` + Testcontainers (Postgres 16, Elasticsearch 8) | pytest + Testcontainers Postgres; recorded Gemini fixtures |
| Contract | Types generated from OpenAPI | OpenAPI diff check in CI | Pydantic schema snapshot test |
| E2E | Playwright: login → add job → see skills → move to Applied → see insights (runs against docker-compose in CI, nightly) | | |
| Coverage gate | 70% lines | 80% lines (JaCoCo) | 80% lines |

## 13. Operations

| Task | How |
|---|---|
| Backup | CronJob 02:00 SGT: `pg_dump --schema=skillsieve -Fc` → Oracle Object Storage via S3-compatible API; keep 7 |
| Restore | `pg_restore` into a scratch Supabase project; tested once per release (record in `docs/runbooks/restore.md`) |
| Reindex ES | `POST /api/admin/reindex` (builds `job-posts-v{n+1}`, swaps alias) |
| Rotate secrets | Re-seal with `kubeseal`, `helm upgrade`, restart pods |
| Upgrade k3s | `curl -sfL https://get.k3s.io | INSTALL_K3S_CHANNEL=stable sh -` during a quiet hour; Helm release is unaffected |

## 14. Release 2 changes (in progress)

| Change | Detail |
|---|---|
| RabbitMQ | Exchange `skillsieve.events` (topic). Queues `skills.extract` (DLQ `skills.extract.dlq`, 3 retries with delay), `core.skills-extracted`. Message: `{eventId, type, jobId, textHash, occurredAt}` (text fetched from DB, not sent in message). Idempotency by `eventId`. |
| Outbox | ☑ `outbox` table (V2). In async mode `JobService` appends `job.created` in the same transaction as the job (create and retry). `OutboxPublisher` polls every 2 s (`FOR UPDATE SKIP LOCKED`, batch 50), publishes with publisher confirms and marks the row sent only after the confirm → at-least-once; stops the batch on the first broker error to keep order. Topology is declared by core-api (`RabbitTopology`): `skills.extract` is a quorum queue dead-lettering to `skills.extract.dlq`. core-api consumes the results (row below). |
| skills-svc worker | ☑ Runs in the skills-svc process when `AMQP_URL` is set (prefetch 1, so one LLM call at a time). For each `job.created` it reads the text from `job_post` by id and runs the normal cached extraction. **Success** → publishes `job.skills.extracted` `{eventId, type, causationId, jobId, textHash, occurredAt, result}` (`result` = the §6 response). **LLM busy/quota/off** → retry ladder `skills.extract.retry.{1,2,3}` (TTL 30 s, 2 min, 10 min, dead-lettering back via `job.created`); after the third it publishes `job.skills.failed` `{…, retryLater: true, reason}` and rejects to the DLQ. **Unexpected error** → `job.skills.failed` with `retryLater: false`, then DLQ. **Bad JSON** → DLQ only. **Job deleted** → ack and drop. core-api declares `core.skills-extracted` (bound to `job.skills.*`). |
| Result consumer | ☑ `SkillsResultListener` on `core.skills-extracted`, one transaction per message: insert `eventId` into `processed_event` (V3; a duplicate is skipped), ignore the event if the job is gone or its `text_hash` differs, then store skills (`job.skills.extracted`) or mark the job (`job.skills.failed`: `retryLater` → PENDING + "try again later", else FAILED). Listener retries 3× (1 s backoff), then the message goes to `core.skills-extracted.dlq`; unreadable JSON goes there at once. |
| Web | ☑ Board and job detail poll while a job is PENDING without an error: every 3 s for ~2 min, then every 30 s for ~15 min, then stop (`features/jobs/polling.ts`). SSE is not needed at this scale. |
| Local cluster | ☑ `ASYNC=true deploy/local/k3d-up.sh` creates `rabbitmq-secrets`, adds the RabbitMQ login to `core-api-secrets` and `AMQP_URL` to `skills-svc-secrets`, and sets `rabbitmq.enabled=true`. |
| Chrome extension | ☑ `extension/` (MV3, TypeScript, no bundler). Permissions: `activeTab` + `scripting` + `storage`; no standing access to job sites. The only host permission is the user's SkillSieve origin, requested at runtime from the options page. On click, `extractJob` runs in the tab (selection ≥ 200 chars → LinkedIn / MyCareersFuture selectors → `main`/`article`, capped at 30,000) and the popup sends `POST /api/jobs` with `Authorization: Bearer ssv_…` and `credentials: 'omit'`. 409 → "already saved" with a link; 401 → "create a new token". |
| Extension tokens | ☑ `ssv_` + 32 random bytes (base64url), stored as SHA-256 (256-bit secret, so no slow hash needed). `AccessTokenFilter` runs before the cookie JWT and builds an in-memory JWT principal (`pat=true`), so `CurrentUser` and roles work unchanged. A token may only call `POST /api/jobs` and `GET /api/me`; anything else is 403, and `X-Requested-With` is still required. Max 10 active per user; `last_used_at` updated on use; revoke in Settings. |
| Keycloak | Realm `skillsieve`, OIDC auth-code + PKCE for web; core-api becomes a resource server (JWT validation via JWKS). |
| Resume tailoring | Part 1 ☑ **storage:** pasted in Settings, stored in `resume` (V5) encrypted with AES-256-GCM (JDK only; random 12-byte nonce; the user id is the associated data, so a row copied to another user fails to decrypt). The text is never logged. `k3d-up.sh` adds `RESUME_KEY` once and never replaces it. Part 2 (next): skills-svc `/internal/tailor` calls a local Ollama model only. Part 3: suggestions panel on job detail, cached by (`content_hmac`, job `text_hash`). Never sent to the free Gemini tier (CLAUDE.md C5). |
