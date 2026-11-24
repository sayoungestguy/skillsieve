import os
from pathlib import Path

import pytest

from app.extraction.ratelimit import LlmBudget
from app.extraction.service import ExtractionService
from app.llm.schema import LlmExtraction
from app.taxonomy.taxonomy import Taxonomy

FIXTURES = Path(__file__).parent / "fixtures" / "gemini"

# Settings for anything that builds the app; real values are never needed in tests.
os.environ.setdefault("DATABASE_URL", "postgresql://unused:unused@localhost:1/unused")
os.environ.setdefault("INTERNAL_TOKEN", "test-internal-token-0123456789")


def load_fixture(name: str) -> LlmExtraction:
    return LlmExtraction.model_validate_json((FIXTURES / f"{name}.json").read_text())


class FakeLlm:
    model = "fake-flash-lite"

    def __init__(self, result: LlmExtraction | Exception) -> None:
        self.result = result
        self.calls: list[str] = []

    async def extract(self, text: str) -> LlmExtraction:
        self.calls.append(text)
        if isinstance(self.result, Exception):
            raise self.result
        return self.result


class MemoryCache:
    def __init__(self) -> None:
        self.rows: dict[tuple[str, str], tuple[str, LlmExtraction]] = {}

    async def get(self, text_hash: str, prompt_version: str) -> tuple[str, LlmExtraction] | None:
        return self.rows.get((text_hash, prompt_version))

    async def put(
        self, text_hash: str, model: str, prompt_version: str, result: LlmExtraction
    ) -> None:
        self.rows[(text_hash, prompt_version)] = (model, result)


TAXONOMY = Taxonomy(
    {
        "java": "java",
        "java 21": "java",
        "spring boot": "spring-boot",
        "kubernetes": "kubernetes",
        "k8s": "kubernetes",
        "postgresql": "postgresql",
        "postgres": "postgresql",
        "kafka": "kafka",
        "docker": "docker",
        "ci/cd": "cicd",
        "terraform": "terraform",
        "aws": "aws",
        "elasticsearch": "elasticsearch",
        "c#": "csharp",
        "node.js": "nodejs",
    }
)


def make_service(
    llm: FakeLlm, cache: MemoryCache | None = None, per_minute: int = 10
) -> ExtractionService:
    return ExtractionService(
        cache=cache or MemoryCache(),
        llm=llm,
        budget=LlmBudget(per_minute, 100),
        taxonomy=TAXONOMY,
        prompt_version="extract-v1",
        max_input_chars=12_000,
        fuzzy_min_score=92.0,
        budget_s=5.0,
    )


@pytest.fixture
def fixture_result() -> LlmExtraction:
    return load_fixture("backend_engineer")
