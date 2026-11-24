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

**Migration ownership:** core-api runs **all** Flyway migrations (`V1__init.sql`, …). skills-svc only reads `skill`/`skill_alias` and reads/writes `extraction_cache`.

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

CREATE INDEX idx_job_post_user_created ON job_post(user_id, created_at DESC);
CREATE INDEX idx_application_status    ON application(status);
CREATE INDEX idx_status_event_app      ON status_event(application_id, changed_at);
```

**Row Level Security:** enabled on every table (`V1__init.sql`) with **no policies**. core-api connects as a dedicated role `skillsieve_api` (not `postgres`, not the Supabase `anon` key). That role runs the migrations, so it owns the tables and bypasses RLS. Every other role, including Supabase's `anon` and `authenticated`, sees nothing. Ownership checks (`user_id = :currentUser`) are enforced in the service layer and covered by `CrossUserAccessIT`. Each connection runs `SET search_path TO skillsieve` (Hikari `connection-init-sql`), so raw SQL resolves our tables. Setup steps: `docs/runbooks/supabase.md`.

**Skill taxonomy seed:** `data/taxonomy/skills.yaml` is the source (slug, name, category, aliases). `scripts/gen_skill_seed.py` turns it into the repeatable migration `R__seed_skills.sql` (upsert, so re-running is safe). Unmapped skills from extraction are reviewed weekly and added to the YAML.

**Text normalisation** (for `text_hash`): Unicode NFKC → lower-case → collapse all whitespace to one space → trim. Implemented identically in Java and Python, with a shared test vector file `data/test-vectors/normalise.json`.

**Size estimate:** 1,000 posts × ~6 KB = ~6 MB. Far below the 500 MB cap.

## 4. Elasticsearch

Index `job-posts` (alias `job-posts-current` → `job-posts-v1`, so we can reindex without downtime).

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

**Interim (Days 3–5):** until Elasticsearch lands in Days 6–8, `GET /api/insights/skills` aggregates `job_skill` in Postgres and `GET /api/search` uses `ILIKE`. Both keep the API shapes below, so the web app doesn't change when ES takes over. `POST /api/admin/reindex` arrives with ES.

**Sync rule:** core-api re-indexes a job document after every change to the job, its status, or its skills (in the same service method, after the DB transaction commits — `@TransactionalEventListener(AFTER_COMMIT)`). If ES is down, the event goes to an `es_outbox` table (R2) / is logged and fixed by `POST /api/admin/reindex` (R1).

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
| POST | `/api/admin/reindex` | — | 202 | OWNER only |

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
| GET | `/healthz` | — | `{status:"ok"}` |
| GET | `/readyz` | — | 200 when taxonomy loaded + DB reachable |

Header `X-Internal-Token` required (shared secret, compared in constant time). Not exposed by ingress. JSON is **camelCase** on the wire (`textHash`, `minYears`, `promptVersion`), the same as core-api; the Python models use snake_case with an alias generator.

Errors: `401` bad token · `422` bad body · `429` LLM budget or Gemini rate limit (core-api keeps the job `PENDING`) · `503` no `GEMINI_API_KEY`, Gemini down, or output invalid twice (also `PENDING`). Any other error makes core-api mark the job `FAILED`.

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

**Evaluation (planned, not built yet):** `services/skills-svc/eval/` will hold 20 labelled job posts (`*.txt` + `*.expected.json`). `uv run python -m eval.run` prints precision/recall per post. Target: precision ≥ 0.9, recall ≥ 0.85. Runs manually (it uses quota), not in CI. CI runs the same pipeline against recorded LLM responses (fixtures).

## 8. Configuration

| Var | Service | Example | Secret? |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | core-api | `jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require&currentSchema=skillsieve` | yes |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | core-api | `skillsieve_api` | yes |
| `JWT_SECRET` | core-api | 64 random bytes, base64 | yes |
| `OWNER_EMAIL` / `OWNER_PASSWORD_HASH` | core-api | seed owner on first start | yes |
| `ES_URL` | core-api | `http://elasticsearch:9200` | no |
| `SKILLS_SVC_URL` | core-api | `http://skills-svc:8000` | no |
| `INTERNAL_TOKEN` | both | random 32 bytes | yes |
| `DEMO_MODE` | core-api | `true` → DEMO role read-only | no |
| `DATABASE_URL` | skills-svc | `postgresql://…?sslmode=require` | yes |
| `GEMINI_API_KEY` | skills-svc | from AI Studio | yes |
| `GEMINI_MODEL` | skills-svc | `gemini-3.5-flash-lite` (default) | no |
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
| R2 | OpenTelemetry SDK in both services → OTel Collector → Prometheus (metrics), Loki (logs), Tempo/Jaeger (traces); Grafana dashboards: API latency p50/p95, error rate, LLM calls/day vs quota, extraction failures, ES heap; alerts to Telegram bot |

Custom metrics: `skillsieve_llm_calls_total{result}`, `skillsieve_llm_cache_hits_total`, `skillsieve_extraction_duration_seconds`, `skillsieve_jobs_saved_total`.

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

## 14. Release 2 changes (planned)

| Change | Detail |
|---|---|
| RabbitMQ | Exchange `skillsieve.events` (topic). Queues `skills.extract` (DLQ `skills.extract.dlq`, 3 retries with delay), `core.skills-extracted`. Message: `{eventId, type, jobId, textHash, occurredAt}` (text fetched from DB, not sent in message). Idempotency by `eventId`. |
| Outbox | `outbox` table in core-api; poller publishes, marks sent → no lost events if broker is down. |
| Chrome extension | MV3, content scripts for LinkedIn/MyCareersFuture, personal access token (hashed in DB) instead of cookie. |
| Keycloak | Realm `skillsieve`, OIDC auth-code + PKCE for web; core-api becomes a resource server (JWT validation via JWKS). |
| Resume tailoring | Resume stored encrypted (AES-GCM, key in Secret). Suggestions generated by Ollama on the developer laptop or a small local model; never sent to the free Gemini tier. |
