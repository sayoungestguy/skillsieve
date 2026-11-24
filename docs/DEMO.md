# Demo video script (about 3 minutes)

Record the screen with QuickTime (**File → New Screen Recording**), or OBS, which is free. Use the local cluster (`deploy/local/k3d-up.sh`) with a Gemini key set, at 1440×900 with browser zoom at 100%. Prepare the two posts in §3 beforehand. The fictional posts in `services/skills-svc/eval/posts/` work well.

| Time | Show | Say (short) |
|---|---|---|
| 0:00–0:15 | README top and the board screenshot | "SkillSieve tracks my job applications and tells me which skills I'm missing. It runs on Kubernetes at zero cost." |
| 0:15–0:30 | Terminal: `kubectl -n skillsieve get pods` (web, core-api, skills-svc, Elasticsearch) | "Four services on k3s, deployed with one Helm chart." |
| 0:30–0:45 | Sign in at `http://localhost:8089` | "Owner login: JWT in an HttpOnly, SameSite cookie." |
| 0:45–1:20 | **Add job** → paste `eval/posts/03_platform_k8s.txt` → Save | "core-api hashes the text, then skills-svc asks Gemini for structured JSON. Contact details are stripped first, and the result is cached by hash." Show the card with its top skills and the detail page with Required and Preferred. |
| 1:20–1:35 | Paste the same post again | "Same text, just reformatted. It's caught as a duplicate, so no second LLM call." |
| 1:35–1:55 | Drag the card to **Applied**, then open **Status history** | "Optimistic locking with a version number, and every change is logged." |
| 1:55–2:20 | **Insights** → gap table → click a gap → **Rate these skills** → set a level → back to Insights | "Elasticsearch aggregations: which skills show up in my posts, and where I'm weak." |
| 2:20–2:35 | **Search** for `kubernetes` | "Full-text search with highlighting. If Elasticsearch is down, it falls back to Postgres." |
| 2:35–2:50 | Terminal: `uv run python -m eval.run` (replays the recording, free) | "A labelled eval set keeps the LLM honest: precision 1.0, recall 0.89." |
| 2:50–3:00 | `docs/DIAGRAMS.md` on GitHub (ER diagram) | "Everything is documented: ER, class and flow diagrams, ADRs, and a CI pipeline with Trivy and Testcontainers." |

Tips: hide the bookmarks bar, close notifications, and use a fresh database (`docker compose -f deploy/local/docker-compose.yml down -v`, then delete the `core-api-secrets` Secret and rerun the script) so the board starts clean. Export as 1080p MP4 and add it to the README as a link, or as a GIF under 10 MB.
