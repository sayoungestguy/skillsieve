# LLM fixtures

`gemini/*.json` are Gemini structured-output responses that tests replay instead of calling the API. No unit test uses the network or quota.

`backend_engineer.json` is hand-written in the exact response shape (`app/llm/schema.py`), because no API key was available when it was created. Once you have a key, replace it with a real recording:

```bash
uv run python -m tests.record "<path to a job post .txt>" > tests/fixtures/gemini/<name>.json
```

Each recording uses one request from the free daily quota.
