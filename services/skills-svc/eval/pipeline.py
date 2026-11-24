"""Runs the real extraction pipeline over the eval posts, replaying recorded LLM output."""

import hashlib
import importlib.util
import json
from pathlib import Path
from typing import Any

from app.extraction.models import ExtractionResult
from app.extraction.ratelimit import LlmBudget
from app.extraction.service import ExtractionService
from app.llm.client import LlmClient
from app.llm.schema import LlmExtraction
from app.taxonomy.taxonomy import Taxonomy

EVAL_DIR = Path(__file__).parent
POSTS = EVAL_DIR / "posts"
RECORDED = EVAL_DIR / "recorded"
REPO = EVAL_DIR.parents[2]
SEED_SCRIPT = REPO / "scripts" / "gen_skill_seed.py"
PROMPT_VERSION = "extract-v1"


def load_taxonomy() -> Taxonomy:
    """Same aliases as the DB seed, read straight from data/taxonomy/skills.yaml."""
    spec = importlib.util.spec_from_file_location("gen_skill_seed", SEED_SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load {SEED_SCRIPT}")
    seed: Any = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(seed)
    aliases: dict[str, str] = {}
    for s in seed.parse(seed.SOURCE.read_text(encoding="utf-8")):
        slug = str(s["slug"])
        for term in [slug, str(s["name"]).lower(), *s.get("aliases", [])]:
            aliases[str(term)] = slug
    return Taxonomy(aliases)


def post_names() -> list[str]:
    return sorted(p.stem for p in POSTS.glob("*.txt"))


def load_post(name: str) -> tuple[str, dict[str, Any]]:
    text = (POSTS / f"{name}.txt").read_text(encoding="utf-8")
    expected = json.loads((POSTS / f"{name}.expected.json").read_text(encoding="utf-8"))
    return text, expected


class _Replay:
    """Cache that serves recorded LLM output and records new output."""

    def __init__(self, name: str, record: bool) -> None:
        self.path = RECORDED / f"{name}.json"
        self.record = record

    async def get(self, text_hash: str, prompt_version: str) -> tuple[str, LlmExtraction] | None:
        if not self.path.exists():
            return None
        data = json.loads(self.path.read_text(encoding="utf-8"))
        return data["model"], LlmExtraction.model_validate(data["output"])

    async def put(
        self, text_hash: str, model: str, prompt_version: str, result: LlmExtraction
    ) -> None:
        if self.record:
            RECORDED.mkdir(exist_ok=True)
            payload = {
                "model": model,
                "prompt_version": prompt_version,
                "output": result.model_dump(),
            }
            self.path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


class _NoLlm:
    model = "none"

    async def extract(self, text: str) -> LlmExtraction:
        raise RuntimeError("no recording and no live LLM; run eval.run with GEMINI_API_KEY")


async def extract(
    name: str, text: str, llm: LlmClient | None = None, taxonomy: Taxonomy | None = None
) -> ExtractionResult:
    service = ExtractionService(
        cache=_Replay(name, record=llm is not None),
        llm=llm or _NoLlm(),
        budget=LlmBudget(per_minute=1000, per_day=1000),  # pacing is done by the runner
        taxonomy=taxonomy or load_taxonomy(),
        prompt_version=PROMPT_VERSION,
        max_input_chars=12_000,
        fuzzy_min_score=92.0,
        budget_s=60.0,
    )
    text_hash = hashlib.sha256(text.encode()).hexdigest()
    return await service.extract(text_hash, text)
