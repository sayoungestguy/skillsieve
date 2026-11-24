"""Evaluate extraction quality on the labelled posts (TECHNICAL_DESIGN §7).

    uv run python -m eval.run            # replay eval/recorded/ (free); Gemini for missing ones
    uv run python -m eval.run --fresh    # re-call Gemini for every post (uses ~20 requests)
    uv run python -m eval.run --check    # exit 1 if below target (precision 0.9, recall 0.85)

Live calls need GEMINI_API_KEY and are paced under the free per-minute limit.
"""

import asyncio
import shutil
import sys

from app.config import get_settings
from app.main import build_llm
from eval.pipeline import RECORDED, extract, load_post, load_taxonomy, post_names
from eval.scoring import PostScore, score, summarise

PRECISION_TARGET = 0.90
RECALL_TARGET = 0.85
SECONDS_PER_CALL = 16  # 4 requests/minute, under the free tier


async def main(argv: list[str]) -> int:
    fresh = "--fresh" in argv
    if fresh and RECORDED.exists():
        shutil.rmtree(RECORDED)
    taxonomy = load_taxonomy()
    llm = None
    scores: list[PostScore] = []
    for name in post_names():
        text, expected = load_post(name)
        needs_call = not (RECORDED / f"{name}.json").exists()
        if needs_call and llm is None:
            llm = build_llm(get_settings())
        if needs_call and scores:
            await asyncio.sleep(SECONDS_PER_CALL)
        result = await extract(name, text, llm if needs_call else None, taxonomy)
        s = score(name, expected, result)
        scores.append(s)
        print(
            f"{name:<24} P {s.precision:4.2f}  R {s.recall:4.2f}  "
            f"fields {s.fields_ok}/{s.fields_total}"
            + (f"  missing {s.missing}" if s.missing else "")
            + (f"  extra {s.extra}" if s.extra else "")
            + (f"  importance {s.wrong_importance}" if s.wrong_importance else "")
            + (f"  unmapped {result.unmapped}" if result.unmapped else ""),
            flush=True,
        )
    total = summarise(scores)
    print(
        f"\nOVERALL precision {total.precision:.3f} (target {PRECISION_TARGET})  "
        f"recall {total.recall:.3f} (target {RECALL_TARGET})  "
        f"importance {total.importance_accuracy:.3f}  fields {total.field_accuracy:.3f}"
    )
    ok = total.precision >= PRECISION_TARGET and total.recall >= RECALL_TARGET
    return 0 if ok or "--check" not in argv else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main(sys.argv[1:])))
