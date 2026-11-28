# Diagrams — SkillSieve

> Version 0.2 · Last updated 2026-11-24 SGT
> Every diagram is drawn from the code as it is today (Mermaid, so GitHub renders them). If you change a table, class or flow, update its diagram in the same PR (CLAUDE.md rule 2).
> Big-picture C4 views live in [ARCHITECTURE.md](ARCHITECTURE.md). This file goes one level deeper.

| # | Diagram | Type |
|---|---|---|
| 1 | [Data model](#1-data-model-er-diagram) | ER diagram |
| 2 | [core-api domain model](#2-core-api-domain-model) | Class diagram |
| 3 | [core-api services and adapters](#3-core-api-services-and-adapters) | Class diagram |
| 4 | [skills-svc](#4-skills-svc) | Class diagram |
| 5 | [web front end](#5-web-front-end) | Module diagram |
| 6 | [Application status](#6-application-status-lifecycle) | State diagram |
| 7 | [Save a job post, end to end](#7-save-a-job-post-end-to-end) | Sequence diagram |
| 8 | [Skill extraction inside skills-svc](#8-skill-extraction-inside-skills-svc) | Flowchart |
| 9 | [Sign-in and session](#9-sign-in-and-session) | Sequence diagram |
| 10 | [Move a card (optimistic locking)](#10-move-a-card-optimistic-locking) | Flowchart |
| 11 | [Search index sync and reindex](#11-search-index-sync-and-reindex) | Flowchart |
| 12 | [Search and insights with fallback](#12-search-and-insights-with-fallback) | Flowchart |
| 13 | [CI/CD pipeline](#13-cicd-pipeline) | Flowchart |
| 14 | [Local cluster deploy (`k3d-up.sh`)](#14-local-cluster-deploy-k3d-upsh) | Flowchart |

---

## 1. Data model (ER diagram)

Postgres schema `skillsieve`, created by `V1__init.sql` and owned by core-api's Flyway. Row Level Security is on for every table with no policies (TECHNICAL_DESIGN §3).

```mermaid
erDiagram
  app_user ||--o{ job_post : "owns"
  app_user ||--o{ user_skill : "rates"
  job_post ||--|| application : "is tracked by"
  application ||--o{ status_event : "records"
  job_post ||--o{ job_skill : "mentions"
  skill ||--o{ job_skill : "appears in"
  skill ||--o{ user_skill : "rated as"
  skill ||--o{ skill_alias : "is known as"
  job_post }o..o| extraction_cache : "text_hash (no FK)"

  app_user {
    uuid id PK
    text email UK
    text password_hash "BCrypt cost 12"
    text role "OWNER | DEMO | USER"
    timestamptz created_at
  }
  job_post {
    uuid id PK
    uuid user_id FK
    text company
    text title
    text location
    text source_url
    text raw_text
    char64 text_hash "UK with user_id"
    text seniority "JUNIOR..LEAD | UNKNOWN"
    smallint min_years
    text extraction_status "PENDING | DONE | FAILED"
    text extraction_error
    timestamptz created_at
    timestamptz updated_at
  }
  application {
    uuid id PK
    uuid job_post_id FK,UK
    text status "SAVED..WITHDRAWN"
    timestamptz applied_at
    date next_action_at
    text notes
    int position
    int version "optimistic lock"
    timestamptz updated_at
  }
  status_event {
    bigserial id PK
    uuid application_id FK
    text from_status
    text to_status
    timestamptz changed_at
  }
  skill {
    serial id PK
    text slug UK
    text name
    text category "LANGUAGE..OTHER"
  }
  skill_alias {
    text alias PK "lower-case"
    int skill_id FK
  }
  job_skill {
    uuid job_post_id PK,FK
    int skill_id PK,FK
    text importance "REQUIRED | PREFERRED"
    text evidence
  }
  user_skill {
    uuid user_id PK,FK
    int skill_id PK,FK
    smallint level "0..3"
    timestamptz updated_at
  }
  extraction_cache {
    char64 text_hash PK
    text model
    text prompt_version
    jsonb result_json "raw LLM output"
    timestamptz created_at
  }
```

| Rule | Where |
|---|---|
| Deleting a user removes their posts, applications, events, job skills and levels | `ON DELETE CASCADE` |
| A skill that is still referenced can't be deleted | `job_skill` / `user_skill` → `ON DELETE RESTRICT` |
| The same text can't be saved twice by one user | `UNIQUE (user_id, text_hash)` → API 409 + `existingId` |
| `skill` and `skill_alias` are seeded, never edited by hand | `R__seed_skills.sql` generated from `data/taxonomy/skills.yaml` |
| `extraction_cache` is shared by everyone (it holds public post text only) | Read and written by skills-svc only |

---

## 2. core-api domain model

JPA entities (package by feature). Controllers never return entities. DTOs are `record`s (diagram 3).

```mermaid
classDiagram
  direction LR
  class AppUser {
    UUID id
    String email
    String passwordHash
    Role role
    Instant createdAt
  }
  class JobPost {
    UUID id
    UUID userId
    String company
    String title
    String location
    String sourceUrl
    String rawText
    String textHash
    String seniority
    Short minYears
    ExtractionStatus extractionStatus
    String extractionError
    Instant createdAt
    Instant updatedAt
    +edit(UpdateJobRequest, Instant)
    +applyExtraction(company, title, location, seniority, minYears, Instant)
    +markExtractionProblem(ExtractionStatus, String, Instant)
    +requestExtraction(Instant)
  }
  class Application {
    UUID id
    Status status
    Instant appliedAt
    LocalDate nextActionAt
    String notes
    int position
    int version
    Instant updatedAt
    +moveTo(Status, Instant) boolean
    +setNextActionAt(LocalDate, Instant)
    +setNotes(String, Instant)
    +setPosition(int, Instant)
  }
  class StatusEvent {
    Long id
    UUID applicationId
    Status fromStatus
    Status toStatus
    Instant changedAt
  }
  class Skill {
    Integer id
    String slug
    String name
    String category
  }
  class JobSkill {
    Key id
    Importance importance
    String evidence
  }
  class UserSkill {
    Key id
    short level
    Instant updatedAt
    +setLevel(int, Instant)
  }
  class Role {
    <<enumeration>>
    OWNER
    DEMO
    USER
  }
  class Status {
    <<enumeration>>
    SAVED
    APPLIED
    INTERVIEW
    OFFER
    REJECTED
    WITHDRAWN
  }
  class ExtractionStatus {
    <<enumeration>>
    PENDING
    DONE
    FAILED
  }
  class Importance {
    <<enumeration>>
    REQUIRED
    PREFERRED
  }

  AppUser "1" --> "*" JobPost : userId
  JobPost "1" <-- "1" Application : jobPost (OneToOne)
  Application "1" --> "*" StatusEvent : applicationId
  JobPost "1" --> "*" JobSkill
  JobSkill "*" --> "1" Skill : skill (ManyToOne)
  AppUser "1" --> "*" UserSkill
  UserSkill "*" --> "1" Skill
  AppUser ..> Role
  Application ..> Status
  JobPost ..> ExtractionStatus
  JobSkill ..> Importance
```

---

## 3. core-api services and adapters

Controller → Service → Repository per feature. Cross-feature work goes through events (`JobChangedEvent`) or narrow adapters (`SkillsClient`, `JobIndex`).

```mermaid
classDiagram
  direction TB
  class AuthController {
    +login(LoginRequest) 204 + Set-Cookie
    +logout() 204
    +me() MeDto
  }
  class JobController {
    +create(CreateJobRequest) 201 JobDto
    +board(status, q, page, size) PageDto~JobSummaryDto~
    +get(id) JobDto
    +update(id, UpdateJobRequest) JobDto
    +delete(id) 204
    +extract(id) 202
  }
  class ApplicationController {
    +update(id, ApplicationPatch) ApplicationDto
    +history(id) List~StatusEventDto~
  }
  class SkillController {
    +list(q, category) List~SkillDto~
    +setLevel(slug, SetLevelRequest) UserSkillDto
  }
  class InsightController {
    +summary() InsightSummaryDto
    +skills(status, from, to, limit) List~SkillInsightDto~
  }
  class SearchController {
    +search(q, page, size) PageDto~SearchHitDto~
  }
  class AdminController {
    +reindex() 202 indexed
  }

  class AuthService
  class LoginRateLimiter {
    +check(clientIp)
  }
  class JobService {
    +create(userId, req) JobDto
    +board(...) PageDto
    +get(userId, jobId) JobDto
    +update(userId, jobId, req) JobDto
    +delete(userId, jobId)
    +requestExtraction(userId, jobId)
  }
  class ApplicationService {
    +update(userId, appId, patch) ApplicationDto
    +history(userId, appId) List
  }
  class ExtractionService {
    +extract(jobId)
  }
  class SkillsClient {
    +extract(textHash, text) Optional~ExtractionResultDto~
  }
  class SkillService
  class InsightService {
    +summary(userId)
    +skills(userId, status, from, to, limit)
  }
  class EsSkillInsights {
    <<static>>
    +query(client, jdbc, ...) List
  }
  class SearchService {
    +search(userId, q, page, size)
  }
  class PostgresSearch {
    +search(...) fallback
  }
  class JobIndex {
    +ensureIndex() @Scheduled 15s
    +sync(jobId, deleted)
    +reindex() int
    +ready() boolean
  }
  class JobIndexListener {
    +on(JobChangedEvent) AFTER_COMMIT
  }
  class JobDocuments {
    +find(jobId) Optional~JobDocument~
    +forEach(sink)
  }
  class JobChangedEvent {
    <<record>>
    UUID jobId
    boolean deleted
  }
  class TextNormaliser {
    <<utility>>
    +normalise(text)
    +hashOf(rawText) sha256
  }
  class ApiExceptionHandler {
    RFC 7807 ProblemDetail
  }

  AuthController --> AuthService
  AuthController --> LoginRateLimiter
  JobController --> JobService
  JobController --> ExtractionService
  ApplicationController --> ApplicationService
  SkillController --> SkillService
  InsightController --> InsightService
  SearchController --> SearchService
  AdminController --> JobIndex

  JobService ..> TextNormaliser
  JobService ..> JobChangedEvent : publishes
  ApplicationService ..> JobChangedEvent : publishes
  ExtractionService ..> JobChangedEvent : publishes
  ExtractionService --> SkillsClient : HTTP, outside DB tx
  JobIndexListener ..> JobChangedEvent : handles
  JobIndexListener --> JobIndex
  JobIndex --> JobDocuments
  SearchService --> JobIndex : ready?
  SearchService --> PostgresSearch : fallback
  InsightService --> EsSkillInsights
  InsightService --> JobIndex : ready?
```

Security filter chain (in order): `ApiRequestGuardFilter` (64 KB cap, `X-Requested-With` on writes) → JWT from the `ss_token` cookie (Nimbus HS256) → role rules (`/api/admin/**` OWNER, `GET` any signed-in user, other writes OWNER/USER, so DEMO is read-only).

---

## 4. skills-svc

FastAPI. `api/` → `extraction/` → adapters (`llm/`, `cache/`, `taxonomy/`). The LLM sits behind a Protocol, so tests and the eval replay recorded output.

```mermaid
classDiagram
  direction LR
  class Routes {
    +POST /v1/extract
    +GET /healthz
    +GET /readyz
    require_internal_token()
  }
  class ExtractionService {
    taxonomy: Taxonomy
    +extract(text_hash, text) ExtractionResult
    -_map(raw, model, cached) ExtractionResult
  }
  class Cache {
    <<Protocol>>
    +get(text_hash, prompt_version)
    +put(text_hash, model, prompt_version, result)
  }
  class CacheRepository {
    asyncpg pool
  }
  class LlmClient {
    <<Protocol>>
    model: str
    +extract(text) LlmExtraction
  }
  class GeminiClient {
    google-genai async
    retry 429/5xx 2s,4s,8s
    one validation retry
  }
  class DisabledLlmClient {
    raises LlmNotConfiguredError
  }
  class LlmBudget {
    per_minute, per_day (Pacific day)
    +acquire()
  }
  class Taxonomy {
    aliases: dict
    +resolve(raw, min_score) slug
  }
  class redact {
    <<function>>
    emails, phones, tracking URLs
  }
  class refresh_taxonomy {
    <<task>>
    every 300 s
  }
  class LlmExtraction {
    <<pydantic>>
    company, title, location
    seniority, min_years
    skills: list~LlmSkill~
  }
  class ExtractionResult {
    <<pydantic camelCase>>
    skills: list~ExtractedSkill~
    unmapped: list~str~
    model, prompt_version, cached
  }
  class LlmError {
    <<exception>>
  }
  class LlmQuotaError
  class LlmUnavailableError
  class LlmNotConfiguredError

  Routes --> ExtractionService
  ExtractionService --> Cache
  ExtractionService --> LlmClient
  ExtractionService --> LlmBudget
  ExtractionService --> Taxonomy
  ExtractionService ..> redact
  CacheRepository ..|> Cache
  GeminiClient ..|> LlmClient
  DisabledLlmClient ..|> LlmClient
  LlmClient ..> LlmExtraction
  ExtractionService ..> ExtractionResult
  refresh_taxonomy --> ExtractionService : swaps taxonomy
  LlmError <|-- LlmQuotaError
  LlmError <|-- LlmUnavailableError
  LlmError <|-- LlmNotConfiguredError
```

| Exception | HTTP | core-api then sets the job to |
|---|---|---|
| `LlmQuotaError` (own budget or Gemini 429) | 429 | `PENDING` + "try again later" |
| `LlmUnavailableError`, `LlmNotConfiguredError` | 503 | `PENDING` + "try again later" |
| bad token / bad body | 401 / 422 | `FAILED` |

---

## 5. web front end

React 18 + TanStack Query. Components never call `fetch`; all HTTP goes through `src/api/`.

```mermaid
flowchart LR
  subgraph routes["App.tsx routes"]
    login["/login → LoginPage"]
    guard["RequireAuth (GET /api/me)"]
    layout["AppLayout: Sidebar · TopBar · MobileTabBar"]
    board["/ → BoardPage (+ /jobs/new AddJobDialog)"]
    detail["/jobs/:id → JobDetailPage"]
    insights["/insights → InsightsPage"]
    skills["/skills → MySkillsPage"]
    search["/search → SearchPage"]
    settings["/settings → SettingsPage"]
  end
  subgraph features["features/*/hooks.ts (TanStack Query)"]
    authH["auth: useMe · useLogin · useLogout · useCanEdit"]
    jobsH["jobs: useJob (polls PENDING) · useHistory · useCreateJob · useUpdateApplication · useRetryExtraction"]
    boardH["board: useBoard · useMoveJob"]
    insightsH["insights: useSkillInsights · useInsightSummary · usePostsWithSkill"]
    skillsH["skills: useSkills · useSetLevel"]
    searchH["search: useSearch"]
  end
  subgraph api["src/api"]
    endpoints["endpoints.ts: authApi · jobsApi · applicationsApi · skillsApi · insightsApi · searchApi"]
    client["client.ts: fetch + credentials + X-Requested-With → ApiError"]
  end
  qc["QueryClient: any 401 → me = null → /login"]
  msw["mocks/ (MSW) when VITE_USE_MOCKS=true"]

  guard --> layout --> board & detail & insights & skills & search & settings
  login --> authH
  board --> boardH
  detail --> jobsH
  insights --> insightsH
  skills --> skillsH
  search --> searchH
  authH & jobsH & boardH & insightsH & skillsH & searchH --> endpoints --> client
  client -. "/api" .-> coreapi[(core-api)]
  client -. "mock mode" .-> msw
  qc -.-> guard
```

---

## 6. Application status lifecycle

The API allows any status change (users fix mistakes). These are the normal paths. Every change writes a `status_event`, and the first move to `APPLIED` sets `applied_at`.

```mermaid
stateDiagram-v2
  [*] --> SAVED : POST /api/jobs
  SAVED --> APPLIED : applied (sets applied_at once)
  SAVED --> WITHDRAWN : not interested
  APPLIED --> INTERVIEW
  APPLIED --> REJECTED
  APPLIED --> WITHDRAWN
  INTERVIEW --> OFFER
  INTERVIEW --> REJECTED
  INTERVIEW --> WITHDRAWN
  OFFER --> WITHDRAWN : declined
  REJECTED --> [*]
  WITHDRAWN --> [*]
  OFFER --> [*] : accepted
```

---

## 7. Save a job post, end to end

R1 is synchronous (ADR-006). Saving never fails because of extraction.

```mermaid
sequenceDiagram
  autonumber
  actor U as User
  participant W as web
  participant A as core-api
  participant P as Postgres
  participant S as skills-svc
  participant G as Gemini
  participant E as Elasticsearch

  U->>W: paste post, Save
  W->>A: POST /api/jobs {rawText, sourceUrl}
  A->>A: NFKC + lower + collapse spaces → SHA-256
  A->>P: find job_post (user_id, text_hash)
  alt already saved
    A-->>W: 409 {existingId}
    W-->>U: "You already saved this post · Open it"
  else new post
    rect rgb(235, 240, 255)
      note over A,P: transaction 1
      A->>P: insert job_post (PENDING) + application (SAVED) + status_event
      A->>A: publish JobChangedEvent
    end
    A->>E: AFTER_COMMIT: index document (refresh=wait_for)
    A->>S: POST /v1/extract {textHash, text} · X-Internal-Token · 10 s timeout
    S->>P: extraction_cache by (text_hash, prompt_version)
    alt cache hit
      P-->>S: raw LLM output
    else cache miss
      S->>S: budget check → redact PII → truncate 12k
      S->>G: generateContent (JSON schema, temperature 0)
      G-->>S: company, title, skills[]
      S->>P: store raw output in extraction_cache
    end
    S->>S: aliases + fuzzy match → canonical slugs, dedupe (REQUIRED wins)
    S-->>A: ExtractionResult
    rect rgb(235, 255, 240)
      note over A,P: transaction 2
      A->>P: replace job_skill rows, fill empty company/title/location, DONE
      A->>A: publish JobChangedEvent
    end
    A->>E: AFTER_COMMIT: re-index with skills
    A-->>W: 201 JobDto
    W-->>U: card on the board with top skills
  end
  note over A,S: skills-svc 429/503/timeout → job stays PENDING + "try later" (Retry button)<br/>other 4xx/5xx → FAILED
```

---

## 8. Skill extraction inside skills-svc

```mermaid
flowchart TD
  req([POST /v1/extract]) --> tok{X-Internal-Token valid?}
  tok -- no --> r401[401]
  tok -- yes --> val{body valid?<br/>64-hex hash, text ≤ 60k}
  val -- no --> r422[422]
  val -- yes --> cache{cache hit for<br/>hash + prompt version?}
  cache -- yes --> map
  cache -- no --> budget{LLM budget left?<br/>4/min · 200/day}
  budget -- no --> r429[429 → job stays PENDING]
  budget -- yes --> key{GEMINI_API_KEY set?}
  key -- no --> r503a[503 → PENDING]
  key -- yes --> redact[redact emails, phones,<br/>tracking URLs · truncate 12k chars]
  redact --> gemini[Gemini generateContent<br/>schema = LlmExtraction · temperature 0]
  gemini --> resp{response?}
  resp -- "429 / 5xx" --> retry{retries left?<br/>2s, 4s, 8s · 9 s budget}
  retry -- yes --> gemini
  retry -- "no (429)" --> r429
  retry -- "no (5xx / timeout)" --> r503b[503 → PENDING]
  resp -- "other 4xx" --> r503b
  resp -- 200 --> valid{valid JSON<br/>for the schema?}
  valid -- "no, first time" --> again[ask again with the<br/>validation errors] --> gemini
  valid -- "no, second time" --> r503b
  valid -- yes --> store[store raw output in extraction_cache]
  store --> map[map each skill: exact alias →<br/>normalised alias → RapidFuzz ≥ 92]
  map --> dedupe[dedupe by slug, REQUIRED wins ·<br/>unknown names → unmapped]
  dedupe --> r200([200 ExtractionResult, camelCase])
```

---

## 9. Sign-in and session

```mermaid
sequenceDiagram
  autonumber
  actor U as User
  participant W as web
  participant A as core-api
  participant P as Postgres

  U->>W: open any page
  W->>A: GET /api/me (cookie if any)
  alt no or expired cookie
    A-->>W: 401
    W-->>U: redirect /login?next=<path>
    U->>W: email + password
    W->>A: POST /api/auth/login · X-Requested-With
    A->>A: rate limit 5/min per client IP
    A->>P: find app_user by email
    A->>A: BCrypt verify (cost 12)
    alt wrong
      A-->>W: 401 problem+json
      W-->>U: "Email or password is wrong."
    else right
      A->>A: sign JWT HS256 {sub, role, exp 1h}
      A-->>W: 204 + Set-Cookie ss_token (HttpOnly, Secure, SameSite=Strict, Path=/api)
      W->>A: GET /api/me
      A-->>W: {id, email, role}
      W-->>U: back to next (same-app paths only)
    end
  else valid cookie
    A-->>W: {id, email, role}
  end
  note over W,A: any later 401 clears the cached user → /login<br/>DEMO role: GET only (writes → 403)
```

---

## 10. Move a card (optimistic locking)

```mermaid
flowchart TD
  drag([User moves a card or changes status]) --> patch["PATCH /api/applications/{id}<br/>{status?, nextActionAt?, notes?, position?, version}"]
  patch --> own{application belongs<br/>to this user?}
  own -- no --> r404[404, never 403]
  own -- yes --> ver{version == current?}
  ver -- no --> r409[409 Stale version → web reloads the board]
  ver -- yes --> st{status sent and different?}
  st -- yes --> move[moveTo: set status · first APPLIED sets applied_at]
  move --> ev[insert status_event · publish JobChangedEvent]
  st -- no --> fields
  ev --> fields[apply sent fields only:<br/>null clears · missing keeps]
  fields --> save[saveAndFlush → version + 1]
  save --> commit[(commit)] --> idx[AFTER_COMMIT: re-index document]
  idx --> r200([200 ApplicationDto with new version])
```

---

## 11. Search index sync and reindex

```mermaid
flowchart TD
  subgraph startup["Startup and every 15 s until ready"]
    s0([JobIndex.ensureIndex]) --> es{ES_URL set?}
    es -- no --> off[Postgres only]
    es -- yes --> reach{ES reachable?}
    reach -- no --> wait[log, retry in 15 s] --> s0
    reach -- yes --> alias{alias job-posts-current exists?}
    alias -- yes --> ready[ready = true]
    alias -- no --> build[reindex]
  end
  subgraph change["On every job / status / skills change"]
    c0([JobChangedEvent, AFTER_COMMIT]) --> rdy{ready?}
    rdy -- no --> skip[skip; the build will include it]
    rdy -- yes --> del{deleted?}
    del -- yes --> d[delete doc by id]
    del -- no --> i[read document from Postgres → index with refresh=wait_for]
    d & i --> fail{ES error?}
    fail -- yes --> warn[log warning; POST /api/admin/reindex repairs drift]
  end
  subgraph reindexing["reindex (startup or POST /api/admin/reindex, OWNER)"]
    build --> n[create job-posts-v1-timestamp with the strict mapping]
    n --> bulk[stream all jobs from Postgres, bulk 200 at a time]
    bulk --> swap[atomic alias swap: remove old, add new]
    swap --> drop[delete old indices] --> ready
  end
```

---

## 12. Search and insights with fallback

```mermaid
flowchart LR
  q([GET /api/search?q=…]) --> e1{ES client and<br/>index ready?}
  e1 -- yes --> es1[multi_match company^3 title^3 body skills.all^2<br/>filter userId · highlight body]
  es1 --> ok1{error?}
  ok1 -- no --> split[split highlight into before / match / after<br/>plain text, no HTML]
  ok1 -- yes --> pg1
  e1 -- no --> pg1[Postgres ILIKE on company, title, raw_text]
  split & pg1 --> out1([Page of SearchHitDto])

  g([GET /api/insights/skills]) --> e2{ES client and<br/>index ready?}
  e2 -- yes --> es2[terms aggs on skills.required / preferred / all<br/>filter userId, status, createdAt range]
  es2 --> ok2{error?}
  ok2 -- no --> join[join names + my level from Postgres<br/>gap = total ≥ 30% and level ≤ 1]
  ok2 -- yes --> pg2
  e2 -- no --> pg2[same numbers with SQL on job_skill]
  join & pg2 --> out2([SkillInsightDto list])
```

---

## 13. CI/CD pipeline

```mermaid
flowchart TD
  push([push or PR]) --> changes[paths-filter]
  changes --> web[web: lint · prettier · typecheck · vitest + coverage · build]
  changes --> core[core-api: seed check · Spotless · Error Prone · unit + Testcontainers PG/ES · SpotBugs · JaCoCo 80%]
  changes --> skills[skills-svc: ruff · format · mypy --strict · pytest + Testcontainers PG · eval targets on recorded output]
  changes --> helm[helm: lint · kubeconform 1.31 on 3 value sets]
  push --> sec[security: Gitleaks · Trivy fs + config]
  web & core & skills & helm & sec --> ci{ci green on main?}
  ci -- no --> stop([stop])
  ci -- yes --> manual{{deploy.yml: manual trigger<br/>while the Oracle VM is on hold}}
  manual --> build[buildx linux/arm64 + amd64: web · core-api · skills-svc → GHCR sha-short]
  build --> scan[Trivy image scan · fail on fixable HIGH/CRITICAL]
  scan --> chart[helm package → push OCI chart to GHCR]
  chart --> ssh[SSH as restricted deploy user → helm upgrade --atomic]
  ssh --> smoke[smoke test /healthz + /api/health]
```

---

## 14. Local cluster deploy (`k3d-up.sh`)

```mermaid
flowchart TD
  start([deploy/local/k3d-up.sh]) --> tools{docker, k3d, helm,<br/>kubectl, git, curl, openssl?}
  tools -- missing --> exit1([exit 1])
  tools -- ok --> pg[docker compose up postgres :55432]
  pg --> cl{k3d cluster exists?}
  cl -- no --> create[k3d cluster create · port 8089 on all interfaces]
  cl -- yes --> ns
  create --> ns[namespace skillsieve]
  ns --> s1{core-api-secrets exists?}
  s1 -- no --> mk1[random JWT secret + owner password<br/>BCrypt via htpasswd → Secret<br/>login → .owner-credentials, git-ignored]
  s1 -- yes --> tokn
  mk1 --> tokn[shared INTERNAL_TOKEN, reused if present]
  tokn --> key{GEMINI_API_KEY has spaces?}
  key -- yes --> exit2([exit 1: paste only the key])
  key -- no --> s2[apply skills-svc-secrets<br/>DB URL · token · key, reused if not given]
  s2 --> img[build web, core-api, skills-svc images<br/>tag sha-short, -dirty if uncommitted]
  img --> imp[k3d image import]
  imp --> up[helm upgrade --install --atomic · values-local.yaml]
  up --> smoke{/healthz and /api/health OK?}
  smoke -- no --> exit3([exit 1])
  smoke -- yes --> done([print localhost + LAN URLs])
```
