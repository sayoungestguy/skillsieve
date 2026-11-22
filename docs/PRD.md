# Product Requirements Document — SkillSieve

> Version 0.1 · Last updated 2026-11-22 22:41 SGT · Owner: Min · Status: Draft

## 1. Problem

Job hunting as a software engineer means reading dozens of job posts. Each post lists 10–20 skills.
Three problems come up:

1. **Lost track.** Applications live in browser tabs, emails and notes. It is easy to forget where you applied, when, and what to follow up.
2. **No big picture.** You read posts one by one. You never see which skills appear in *most* posts, so you do not know what to learn first.
3. **Generic resumes.** Every resume looks the same because tailoring takes too long.

## 2. Goals and non-goals

### Goals
| # | Goal | Release |
|---|---|---|
| G1 | Save a job post in under 10 seconds (paste text or URL) | R1 |
| G2 | Track each application through a clear status pipeline | R1 |
| G3 | Auto-extract skills from each post, with required vs preferred | R1 |
| G4 | Show skill frequency across all saved posts vs my own skill levels | R1 |
| G5 | Save posts from job sites with one click | R2 |
| G6 | Suggest resume changes per job, privately | R2 |
| G7 | Let friends (church, peers) use it with their own accounts | R2 |

### Non-goals
- Not a job board. SkillSieve does not list or scrape jobs for discovery.
- No auto-apply bots.
- No paid features, no payments.
- No native iOS app (Apple fee). Mobile access is through the responsive web app (PWA in R2).

## 3. Users

| Persona | Description | Main need |
|---|---|---|
| **P1 — Min (primary)** | Full-stack engineer, ~2.5 yrs, job hunting, upskilling at the same time | Track applications, know what to learn next |
| **P2 — Friend job-seeker** | Church/volunteer friend looking for a job, may not be technical | Simple tracker, clear next steps (R2, multi-user) |
| **P3 — Interviewer (indirect)** | Hiring manager who opens the GitHub repo | See clean code, real infra, good docs, a live demo |

## 4. User stories and acceptance criteria

### Epic A — Capture jobs (R1)

**A1. Paste a job post**
As a job-seeker, I want to paste a job post's text so that it is saved without retyping.
- Given I open "Add job" and paste text (≥ 200 characters), when I press Save, then a job card appears in the **Saved** column within 2 seconds.
- Company, title and location are pre-filled by the LLM; I can edit them before or after saving.
- If the same text (same hash) already exists, I see "Already saved" with a link to it. No duplicate is created.

**A2. Add a source URL**
- I can add an optional URL. The URL is stored and shown as a link. (R1 does not fetch the URL.)

**A3. Save from the browser (R2)**
- A Chrome extension button on LinkedIn / MyCareersFuture / Indeed sends the visible job text and URL to SkillSieve.

### Epic B — Track applications (R1)

**B1. Status board**
- Columns: Saved, Applied, Interview, Offer, Rejected, Withdrawn.
- I can drag a card between columns, or change status from the detail page.
- Each change is stored with a timestamp (status history).

**B2. Notes and follow-ups**
- Each application has free-text notes and an optional "next action" date.
- Cards with a next-action date today or earlier show a red dot.

**B3. Search**
- I can search all saved posts by keyword (company, title, skills, text). Results come back in < 500 ms for 1,000 posts.

### Epic C — Skills and gaps (R1)

**C1. Skill extraction**
- After saving, the system extracts skills and tags each as **required** or **preferred**.
- Skill names are normalised (e.g. "k8s", "Kubernetes" → `kubernetes`).
- Extraction finishes in < 10 s (R1 sync). If the LLM fails, the job is still saved and shows "Skills pending — retry".

**C2. My skills profile**
- I can rate my level for any skill: 0 None · 1 Aware · 2 Working · 3 Strong.

**C3. Gap dashboard**
- Shows top 20 skills by % of saved posts that mention them, split required / preferred.
- Each row shows my level. A skill with frequency ≥ 30% and my level ≤ 1 is flagged **Gap**.
- I can filter by status (e.g. only posts I applied to) and by date range.

### Epic D — Resume help (R2)
- I upload my resume once (stored privately).
- For a job, I get 3–5 suggested bullet changes that match the post's skills.
- Resume text never goes to a free cloud LLM (local Ollama only, or redacted).

### Epic E — Accounts (R1 single user → R2 multi-user)
- R1: one owner account (username + password, BCrypt hash). Public demo mode shows seeded sample data, read-only.
- R2: Keycloak login; each user sees only their own data.

## 5. Functional requirements summary

| ID | Requirement | Priority | Release |
|---|---|---|---|
| FR-1 | Create, read, update, delete job posts | Must | R1 |
| FR-2 | Duplicate detection by SHA-256 of normalised text | Must | R1 |
| FR-3 | Application status pipeline + history | Must | R1 |
| FR-4 | LLM skill extraction with JSON schema output | Must | R1 |
| FR-5 | Skill taxonomy with aliases | Must | R1 |
| FR-6 | User skill levels | Must | R1 |
| FR-7 | Gap dashboard (ES aggregations) | Must | R1 |
| FR-8 | Full-text search | Should | R1 |
| FR-9 | Read-only demo mode | Should | R1 |
| FR-10 | Async extraction via queue | Should | R2 |
| FR-11 | Chrome extension | Should | R2 |
| FR-12 | Resume tailoring (private) | Could | R2 |
| FR-13 | Multi-user via Keycloak | Could | R2 |

## 6. Non-functional requirements

| ID | Area | Requirement |
|---|---|---|
| NFR-1 | Cost | $0/month. Free tiers only. |
| NFR-2 | Performance | p95 API latency < 300 ms (excluding LLM calls). Board loads < 1.5 s on 4G. |
| NFR-3 | Availability | Best effort, single node. Target 99% monthly. |
| NFR-4 | Security | HTTPS only. OWASP Top 10 checks. No secrets in Git. Image scan in CI. |
| NFR-5 | Privacy | No PII sent to free LLM tiers. Data stored in Singapore region (Supabase). |
| NFR-6 | Resource | Whole cluster fits in 12 GB RAM / 2 OCPU with ≥ 15% headroom. |
| NFR-7 | Data safety | Nightly `pg_dump`, 7-day retention, restore tested once per release. |
| NFR-8 | Accessibility | WCAG 2.1 AA for colour contrast and keyboard use. |
| NFR-9 | Mobile | Usable on a 375 px wide phone screen. |

## 7. Success metrics

| Metric | Target (end of R1) |
|---|---|
| Job posts saved by me | ≥ 30 |
| Skill extraction accuracy (manual check of 20 posts) | ≥ 85% of skills correct, ≤ 10% false positives |
| LLM calls per saved post | ≤ 1 (cache works) |
| CI pipeline time | < 10 min |
| Monthly cost | $0 |
| Interviews where I demo SkillSieve | ≥ 1 |

## 8. Release plan

### Release 1 — MVP (Days 1–10)
| Day | Deliverable | Done |
|---|---|---|
| 1–2 | Oracle VM, k3s, DuckDNS, HTTPS, hello-world via GitHub Actions | ☐ |
| 3–5 | core-api (jobs, applications, status history) + Supabase + web board | ☐ |
| 6–8 | skills-svc + Gemini + taxonomy + Elasticsearch + gap dashboard | ☐ |
| 9–10 | Helm chart polish, Trivy, README, architecture diagram, demo video | ☐ |

### Release 2 (Days 11–14+)
RabbitMQ async pipeline · Chrome extension · Prometheus/Grafana/Loki · resume tailoring · Keycloak.

## 9. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Oracle ARM capacity not available | Blocks deploy | Retry; meanwhile run on local k3d; fall back to 2× AMD micro VMs for a demo |
| Gemini free quota too small | Extraction stops | Flash-Lite default, hash cache, backoff, manual "retry later" state |
| LLM extraction noisy | Bad gap data | Taxonomy normalisation, JSON schema, eval set of 20 labelled posts |
| Supabase project pauses | App down | Real weekly use + GitHub Actions keep-alive query every 3 days |
| 12 GB RAM too tight | Pods OOM-killed | Resource limits, ES heap 1 GB, RAM budget review each release |

## 10. Open questions
- Which job sites to support first in the extension? (Proposed: LinkedIn, MyCareersFuture.)
- Keep demo data public, or protect the whole site behind login? (Proposed: public read-only demo.)
