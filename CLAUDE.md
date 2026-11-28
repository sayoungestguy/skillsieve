# CLAUDE.md — SkillSieve

> Version 0.1 · Last updated 2026-11-22 22:41 SGT
> Read this file first in every session. Keep it short. Details live in `docs/`.

## 1. What this project is

**SkillSieve** — "Sift job posts. Find your gaps. Land the offer."

A job-search tracker for software engineers. You paste a job post. The app:
1. Saves it and tracks the application status (Saved → Applied → Interview → Offer).
2. Uses an LLM to pull out the required and preferred skills.
3. Shows a skill-gap dashboard across all saved posts ("Docker is in 60% of posts; you are at level 1").

It is a **portfolio project**. The code, infra and docs must show production habits:
Kubernetes, CI/CD, tests, security scanning, observability, clear docs.

## 2. Hard constraints (do not break)

| # | Constraint | What it means for you |
|---|---|---|
| C1 | **$0 running cost** | Only free tiers. Never add a paid service, paid API, or a cloud resource that can bill. Ask first if unsure. |
| C2 | **Oracle Always Free ARM VM: 2 OCPU, 12 GB RAM** | Every image must build for `linux/arm64` (and `linux/amd64` for local dev). Every pod must set CPU/memory requests and limits. See RAM budget in `docs/ARCHITECTURE.md`. |
| C3 | **Supabase free plan** | Postgres ≤ 500 MB, Storage ≤ 1 GB, no automatic backups (we run our own `pg_dump` CronJob). Schema: `skillsieve`. |
| C4 | **Gemini API free tier** | Small daily quota. Use Flash-Lite by default. Cache every extraction by text hash. Never call the LLM twice for the same text. |
| C5 | **Privacy** | Free-tier LLM data may be used for training. **Never send resume text, names, emails or phone numbers to Gemini.** Job posts (public text) are OK. Resume features use a local Ollama model or are redacted first. |

## 3. Stack

| Layer | Tech |
|---|---|
| Web | React 18 + Vite + TypeScript, TanStack Query, React Router, Tailwind CSS |
| Core API | Java 21, Spring Boot 3, Spring Data JPA, Flyway, Elasticsearch Java client |
| Skills service | Python 3.12, FastAPI, Pydantic v2, `google-genai` SDK |
| Database | Supabase Postgres (schema `skillsieve`) |
| Search / analytics | Elasticsearch 8 (single node, 1 GB heap) |
| Runtime | k3s on Oracle Always Free ARM VM, Traefik ingress, cert-manager + Let's Encrypt |
| Packaging | Docker (multi-stage, non-root), Helm chart `deploy/helm/skillsieve` |
| CI/CD | GitHub Actions → GHCR → Helm upgrade over SSH |
| Security | Trivy image + IaC scan, Gitleaks, Dependabot, Sealed Secrets |
| Release 2 | RabbitMQ (async extraction), Chrome extension (MV3), Prometheus + Grafana + Loki, Keycloak (OIDC), resume tailoring |

## 4. Repo map

```
skillsieve/
├── CLAUDE.md                 ← you are here
├── README.md
├── docs/
│   ├── PRD.md                ← what and why (scope, user stories)
│   ├── ARCHITECTURE.md       ← big picture, diagrams, ADRs, RAM budget
│   ├── DIAGRAMS.md           ← ER, class, state, sequence and flow diagrams (keep in sync)
│   ├── DESIGN.md             ← UI/UX: screens, tokens, components
│   ├── TECHNICAL_DESIGN.md   ← schemas, APIs, LLM contract, CI/CD detail
│   └── CODING_STANDARDS.md   ← how we write code, commits, tests
├── apps/web/                 ← React front end
├── services/core-api/        ← Spring Boot
├── services/skills-svc/      ← FastAPI + LLM
├── extension/                ← Chrome extension (Release 2)
├── deploy/
│   ├── helm/skillsieve/      ← Helm chart (all services)
│   ├── k3s/                  ← VM bootstrap scripts (k3s, cert-manager, sealed-secrets)
│   └── local/                ← docker-compose for local dev
├── data/taxonomy/skills.yaml ← canonical skill names + aliases
└── .github/workflows/        ← CI/CD
```

## 5. Commands

```bash
# Local dev (all services + Postgres + Elasticsearch)
docker compose -f deploy/local/docker-compose.yml up -d

# Web
cd apps/web && npm ci && npm run dev        # http://localhost:5173
npm run lint && npm run typecheck && npm test

# Core API
docker compose -f deploy/local/docker-compose.yml up -d postgres        # Postgres on :55432
cd services/core-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # :8080
./mvnw verify   # Spotless, Error Prone, unit + Testcontainers ITs, SpotBugs, 80% coverage
python3 scripts/gen_skill_seed.py   # after editing data/taxonomy/skills.yaml

# Skills service
cd services/skills-svc && cp .env.example .env && uv sync && uv run uvicorn app.main:app --reload --port 8000
uv run ruff check . && uv run ruff format --check . && uv run mypy app tests && uv run pytest   # Docker needed for the Postgres IT

# Chrome extension (load extension/dist at chrome://extensions → Load unpacked)
cd extension && npm ci && npm test && npm run build

# Self-hosted cluster (k3d, LAN): build + helm upgrade + smoke test
deploy/local/k3d-up.sh
ASYNC=true deploy/local/k3d-up.sh   # R2: RabbitMQ + queued extraction
OBSERVABILITY=true deploy/local/k3d-up.sh   # R2: Prometheus + Grafana at /grafana

# Helm
helm lint deploy/helm/skillsieve
helm template deploy/helm/skillsieve -f deploy/helm/skillsieve/values-prod.yaml
```

## 6. Rules for working in this repo

1. **Read the right doc before you change things.** Feature → `PRD.md`. Structure → `ARCHITECTURE.md`. Schema/API → `TECHNICAL_DESIGN.md`. UI → `DESIGN.md`. Style → `CODING_STANDARDS.md`.
2. **Update the docs in the same PR** when you change an API, schema, env var, or architecture decision.
3. **No new dependency** without saying why in the PR description. Prefer the standard library.
4. **No secrets in Git.** Use `.env` locally (git-ignored) and Sealed Secrets in the cluster.
5. **Every pod gets resource requests/limits.** Every image builds for arm64.
6. **Tests come with the code.** A feature without tests is not done.
7. **Small PRs.** One concern per PR. Conventional Commits (`feat:`, `fix:`, `chore:` …).
8. **Ask before** anything irreversible (DB drops, force-push, deleting data) or anything that might cost money.

## 7. Definition of Done

- [ ] Acceptance criteria in `PRD.md` met
- [ ] Unit tests + at least one integration test pass in CI
- [ ] Lint, type check, format check pass
- [ ] Trivy scan has no HIGH/CRITICAL that we can fix
- [ ] Docs updated (API, env vars, diagrams)
- [ ] Deployed to the cluster and smoke-tested at the public URL

## 8. Current phase

**Release 1 (Days 1–10).** Track progress in `docs/PRD.md §8`.

| Day | Milestone | Status |
|---|---|---|
| 1–2 | Oracle VM + k3s + DuckDNS + HTTPS + hello-world deploy via Actions | ◐ chart, CI and bootstrap done; Oracle on hold, self-hosted on k3d (LAN) |
| 3–5 | Core API + Supabase + web status board | ◐ core-api + web login on real API (local Postgres, k3d); Supabase project pending |
| 6–8 | Skills service + Gemini + Elasticsearch gap dashboard | ☑ on k3d (eval: P 1.00 / R 0.89); deploy to a public host pending |
| 9–10 | Helm chart polish, full pipeline, README, diagram, demo video | ◐ Trivy clean, helm test, README + diagrams done; demo video to record (docs/DEMO.md) |
