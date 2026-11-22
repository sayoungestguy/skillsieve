# Coding Standards — SkillSieve

> Version 0.1 · Last updated 2026-11-22 22:41 SGT
> Tools enforce most of this. If a tool and this doc disagree, fix the doc or the tool config — not your code by hand each time.

## 1. General rules

1. **Readable first.** Clear names beat comments. Comments explain *why*, not *what*.
2. **Small units.** Functions ≤ ~40 lines, files ≤ ~400 lines. Split when bigger.
3. **Fail loudly and early.** Validate input at the edge (controller / API handler). Never swallow exceptions.
4. **No magic values.** Use named constants, enums or config.
5. **Package by feature.** `job/`, `application/`, `skill/` — not `controllers/`, `services/`.
6. **Same names everywhere.** `jobPost` in Java/TS, `job_post` in SQL/Python. Status values are UPPER_SNAKE everywhere.
7. **Time.** Store UTC (`TIMESTAMPTZ`). Show SGT in the UI. Use ISO-8601 in APIs.
8. **Money/cost rule.** Do not add a library, service or API that can cost money. See CLAUDE.md C1.

## 2. Git workflow

- **Trunk-based.** `main` is always deployable. Short-lived branches: `feat/board-drag-drop`, `fix/es-reindex-alias`, `chore/deps`.
- **Conventional Commits:**
  ```
  feat(core-api): add duplicate detection by text hash
  fix(web): keep card order after drag between columns
  docs(tdd): document extraction_cache table
  chore(deps): bump spring-boot to 3.x.y
  ```
  Types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`. Scope = `web`, `core-api`, `skills-svc`, `helm`, `ci`, `docs`.
- **PRs:** one concern, < ~400 changed lines where possible. Squash-merge. PR template must be filled:
  ```
  ## What
  ## Why (link PRD story / issue)
  ## How to test
  ## Screenshots (UI changes)
  ## Checklist
  - [ ] Tests added/updated
  - [ ] Docs updated (API / env / diagrams)
  - [ ] No secrets, no PII in logs
  ```
- **Branch protection on `main`:** CI must pass, linear history, no force-push.
- **Tags:** `v0.1.0` = R1, `v0.2.0` = R2 (SemVer). Release notes from commits.

## 3. Java / Spring Boot (core-api)

| Topic | Rule |
|---|---|
| Version | Java 21, Spring Boot 3.x |
| Format | Google Java Format via **Spotless** (`./mvnw spotless:apply`); CI runs `spotless:check` |
| Static analysis | Error Prone + SpotBugs (fail on high) |
| DTOs | Java `record`s. Never expose JPA entities from controllers. |
| Injection | Constructor injection only. No `@Autowired` on fields. Classes `final` where possible. |
| Layers per feature | `XController` → `XService` → `XRepository`; mapping in `XMapper` (MapStruct or hand-written static methods) |
| Transactions | `@Transactional` on service methods only; read-only methods `@Transactional(readOnly = true)` |
| Validation | Jakarta Bean Validation on request records (`@NotBlank`, `@Size`) + `@Valid` |
| Errors | Throw domain exceptions (`JobNotFoundException`); one `@RestControllerAdvice` maps them to RFC 7807 `ProblemDetail` |
| Null | Use `Optional` for return values that may be empty; never for fields or parameters |
| Logging | SLF4J, structured JSON (logstash encoder). `log.info("job.saved jobId={}", id)`. No PII, no raw job text. |
| DB | Flyway migrations `V{n}__{snake_description}.sql`; never edit an applied migration; repeatable seeds `R__*.sql` |
| Tests | JUnit 5, AssertJ, Mockito. Names: `shouldReturn409WhenTextHashExists()`. Integration tests with Testcontainers end in `IT`. |

## 4. Python / FastAPI (skills-svc)

| Topic | Rule |
|---|---|
| Version | Python 3.12, managed with **uv** (`pyproject.toml` + `uv.lock`) |
| Lint + format | **ruff** (`ruff check`, `ruff format`), line length 100 |
| Types | **mypy --strict**. No untyped defs. Avoid `Any`. |
| Models | Pydantic v2 for all request/response and LLM output |
| Structure | `api/` (routers) → `extraction/` (use cases) → `llm/`, `taxonomy/`, `cache/` (adapters) |
| Config | `pydantic-settings`; one `Settings` object; no `os.getenv` scattered in code |
| Async | Use `async def` for I/O endpoints; use `httpx.AsyncClient` / async DB driver (`asyncpg`) |
| Errors | Raise `HTTPException` only in routers; domain code raises custom exceptions |
| Logging | `structlog` JSON; include `trace_id`, never the job text or PII |
| Tests | pytest + pytest-asyncio; LLM calls replaced by recorded fixtures (`tests/fixtures/gemini/*.json`); no network in unit tests |
| Prompts | Stored as files in `app/llm/prompts/`, versioned (`extract_v1.txt`). Changing a prompt = new version file. |

## 5. TypeScript / React (web)

| Topic | Rule |
|---|---|
| Version | TypeScript strict mode (`"strict": true`, `noUncheckedIndexedAccess`) |
| Lint + format | ESLint (typescript-eslint, react-hooks, jsx-a11y) + Prettier |
| Types | No `any`. API types generated from OpenAPI (`npm run gen:api`). |
| Components | Function components + hooks. One component per file, `PascalCase.tsx`. Props typed with `interface`. |
| State | Server state: **TanStack Query**. Local UI state: `useState`/`useReducer`. No global store unless needed. |
| Data access | All HTTP calls in `src/api/`; components never call `fetch` directly |
| Styling | Tailwind CSS using tokens from DESIGN.md (`tailwind.config.ts`). No inline hex colours. |
| Forms | React Hook Form + Zod schemas |
| Accessibility | Semantic HTML first; every interactive element reachable by keyboard; `jsx-a11y` errors fail CI |
| Security | Never `dangerouslySetInnerHTML`. Job text rendered as plain text. |
| Tests | Vitest + React Testing Library; query by role/label, not by class names; MSW for API mocks |
| Files | `features/<feature>/{components,hooks,api.ts,types.ts}`; shared UI in `components/ui/` |

## 6. SQL and data

- Tables and columns: `snake_case`, singular table names (`job_post`).
- Every table has a primary key; foreign keys with explicit `ON DELETE`.
- Add an index for every foreign key used in filters and for every `WHERE`/`ORDER BY` on hot paths.
- Use `TIMESTAMPTZ`, `TEXT` (not `VARCHAR(n)` unless there is a real limit), `UUID` IDs for user-facing rows.
- Parameterised queries only. No string-built SQL.
- Elasticsearch mappings are `"dynamic": "strict"`; change mapping = new index version + alias swap.

## 7. Docker

- Multi-stage builds; final image: distroless or `-slim`/`alpine` base, **pinned by digest**.
- Run as non-root (`USER 10001`). Read-only root filesystem where possible.
- Build for `linux/arm64` and `linux/amd64` (`docker buildx`).
- One process per container. Health check endpoint, not `HEALTHCHECK` shell scripts.
- `.dockerignore` excludes `.git`, `node_modules`, `target`, `.venv`, `.env*`.
- JVM flags: `-XX:MaxRAMPercentage=75 -XX:+UseSerialGC` (small heap, single node).

## 8. Kubernetes and Helm

- Every container: `resources.requests` + `resources.limits`, `readinessProbe`, `livenessProbe`.
- `securityContext`: `runAsNonRoot: true`, `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`.
- No `latest` tags. Images tagged `sha-<short>`.
- All values configurable via `values.yaml`; environment overrides in `values-prod.yaml`.
- Secrets only via `SealedSecret`. Never commit a plain `Secret`.
- Labels: `app.kubernetes.io/{name,instance,component,part-of,version}`.
- `helm lint` and `kubeconform` must pass in CI.

## 9. Security rules

1. No secrets, tokens or keys in code, logs, commits or issues. Gitleaks runs in CI.
2. No PII to the free LLM tier (see CLAUDE.md C5). Redaction function must be called before every LLM request.
3. Every endpoint checks the current user owns the resource. Add a test for cross-user access.
4. Validate and size-limit all input.
5. Fixable HIGH/CRITICAL vulnerabilities block the deploy.
6. Dependencies: only well-maintained packages; review the licence (MIT/Apache/BSD preferred).

## 10. Testing rules

- New feature = unit tests + at least one integration test.
- Bug fix = a test that fails before the fix.
- Tests are fast and independent; no order dependence; no real network (except Testcontainers).
- Coverage gates: core-api 80%, skills-svc 80%, web 70% (lines). Do not game coverage with empty tests.
- Test names describe behaviour: `should…When…`.

## 11. Documentation rules

- Update `docs/` in the same PR as the change (API, schema, env var, architecture).
- Diagrams in **Mermaid** inside Markdown so they render on GitHub and diff in Git.
- Each doc has a version line at the top; bump the date when you edit it.
- Architecture decisions → add a row to the ADR table in ARCHITECTURE.md.

## 12. AI-assisted development (Claude Code, etc.)

- AI output is a draft. The author reviews every line before commit and is responsible for it.
- Give the AI the relevant doc section (CLAUDE.md, TECHNICAL_DESIGN.md) instead of re-explaining.
- Ask for tests with every generated feature; run them locally before pushing.
- Never paste secrets, real resumes or other people's data into an AI prompt.
- Keep generated code consistent with this document; run the formatters after generation.
