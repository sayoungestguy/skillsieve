# Demo video script (about 3 minutes)

## Automated recording (Playwright)

The browser part of the demo is recorded by Playwright (`apps/web/e2e/demo.record.ts`): a clean throwaway account, the Release 1 flow below with on-screen captions and a visible pointer, then the Grafana dashboards when observability is on. About 1:40, 1440×900, WebM.

```bash
ASYNC=true OBSERVABILITY=true deploy/local/k3d-up.sh   # stack with a Gemini key set
cd apps/web && E2E_EMAIL=alex.demo@skillsieve.local npm run demo
```

The video is written to `apps/web/e2e/.results/demo.record.ts-SkillSieve-demo-demo/video.webm` (copy it to `docs/demo/`, which is git-ignored). Upload it to GitHub (a release asset, or drag it into the README editor) rather than committing it. It uses the installed Google Chrome (`DEMO_CHANNEL` to change). The account is created at start and deleted at the end; the fixture posts are cached by text hash after the first run, so re-recording costs no Gemini quota. Add the terminal segments below (pods, eval) by hand if you want them.

## Manual script

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
