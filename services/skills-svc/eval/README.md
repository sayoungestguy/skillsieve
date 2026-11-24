# Extraction eval set

20 **original, fictional** job posts (`posts/*.txt`) with hand labels (`posts/*.expected.json`). They aren't copied from real listings. They cover backend, frontend, data, ML, mobile, security, SRE, QA and lead roles, plus tricky cases: alias spellings (K8s, Postgres), "nice to have" sections, combined items (`Docker/Kubernetes`), soft skills named as requirements, skills outside the taxonomy, contact details that must be redacted, and a prompt-injection line (post 12).

**Label rules:** skills use taxonomy slugs, with importance taken from the section they appear in. `seniority` is set only when the post states a level (otherwise `UNKNOWN`, matching the prompt). `min_years` is set only when stated. `unmapped_examples` is informational and not scored.

```bash
uv run python -m eval.run           # replay recorded/ (free); calls Gemini only for posts with no recording
uv run python -m eval.run --fresh   # re-record all 20 (uses ~20 free-tier requests, ~6 min)
uv run python -m eval.run --check   # exit 1 below target
```

`recorded/` holds the raw Gemini output, and alias mapping runs again on replay. So taxonomy changes are evaluated for free, and `tests/test_eval.py` enforces the targets in CI with no network.

## Results (gemini-3.5-flash-lite, prompt extract-v1, 2026-11-24)

| | Precision | Recall | Importance | Fields |
|---|---|---|---|---|
| First run | 1.000 | 0.849 | 1.000 | 0.750 |
| After fixing 15 label errors and adding aliases | **1.000** | **0.893** | **1.000** | **0.975** |
| Target | 0.90 | 0.85 | | |

- **Label errors were mine.** I had guessed seniority, title or years where the post didn't state them. The model followed the prompt correctly.
- **Aliases came from this run's unmapped list,** such as Vitest, Playwright, OWASP Top 10, AWS Lambda, Elastic stack and PyTorch. That's the normal taxonomy review process, but it means the recall gain is partly tuned on this set. Add new held-out posts before trusting it as an unbiased number.
- **Remaining misses are the model's:** skills listed only under "Responsibilities" (post 04), soft and practice skills in a lead role (post 11), and `REST` implied by "REST APIs" / "Django REST Framework". The injection in post 12 was ignored (precision 1.0).
