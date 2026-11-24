# skills-svc

Internal FastAPI service: job post text → company, title, seniority and canonical skills. It uses Gemini Flash-Lite with structured output and caches every result by text hash. Contract: `docs/TECHNICAL_DESIGN.md` §6–7.

## Get a free Gemini API key

1. Open <https://aistudio.google.com/apikey> and sign in with a Google account.
2. **Create API key**, in a new project. Don't link a billing account; the free tier needs none (CLAUDE.md C1).
3. Keep the key out of Git. Use it in one of these places:
   - local cluster: `GEMINI_API_KEY=… deploy/local/k3d-up.sh` (stored in the cluster Secret, reused on later runs);
   - local dev server: `services/skills-svc/.env` (git-ignored).

Free-tier data may be used by Google for training, so the service strips emails, phone numbers and tracking URLs before sending, and resume text is never sent (CLAUDE.md C5).

## Run and test

```bash
cp .env.example .env && uv sync
```

```bash
uv run uvicorn app.main:app --reload --port 8000
```

```bash
uv run ruff check . && uv run ruff format --check . && uv run mypy app tests && uv run pytest
```

`pytest` replays LLM fixtures (`tests/fixtures/gemini/`) and starts a Postgres container for the integration test. It never calls Gemini. To record a real fixture, which costs 1 request, see `tests/fixtures/README.md`.
