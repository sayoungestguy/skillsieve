"""Use case: text → (cache | LLM) → canonical skills (TECHNICAL_DESIGN §7)."""

import asyncio
import time
from typing import Protocol

import structlog

from app.extraction.models import ExtractedSkill, ExtractionResult
from app.extraction.ratelimit import LlmBudget
from app.extraction.redact import redact
from app.llm.client import LlmClient
from app.llm.errors import LlmQuotaError, LlmUnavailableError
from app.llm.schema import LlmExtraction
from app.metrics import CACHE_HITS, EXTRACTION_SECONDS, LLM_CALLS, LLM_USED_TODAY
from app.taxonomy.taxonomy import Taxonomy

log = structlog.get_logger()

EVIDENCE_MAX = 120


class Cache(Protocol):
    async def get(
        self, text_hash: str, prompt_version: str
    ) -> tuple[str, LlmExtraction] | None: ...

    async def put(
        self, text_hash: str, model: str, prompt_version: str, result: LlmExtraction
    ) -> None: ...


class ExtractionService:
    def __init__(
        self,
        cache: Cache,
        llm: LlmClient,
        budget: LlmBudget,
        taxonomy: Taxonomy,
        prompt_version: str,
        max_input_chars: int,
        fuzzy_min_score: float,
        budget_s: float,
    ) -> None:
        self._cache = cache
        self._llm = llm
        self._budget = budget
        self.taxonomy = taxonomy
        self._prompt_version = prompt_version
        self._max_input_chars = max_input_chars
        self._fuzzy_min_score = fuzzy_min_score
        self._budget_s = budget_s

    async def extract(self, text_hash: str, text: str) -> ExtractionResult:
        start = time.perf_counter()
        hit = await self._cache.get(text_hash, self._prompt_version)
        if hit is not None:
            model, raw = hit
            log.info("extract.cache_hit", text_hash=text_hash[:12])
            CACHE_HITS.inc()
            result = self._map(raw, model, cached=True)
            EXTRACTION_SECONDS.labels("true").observe(time.perf_counter() - start)
            return result

        try:
            await self._budget.acquire()
        except LlmQuotaError:
            LLM_CALLS.labels("quota").inc()
            raise
        LLM_USED_TODAY.set(self._budget.used_today)
        safe_text = redact(text)[: self._max_input_chars]
        try:
            async with asyncio.timeout(self._budget_s):
                raw = await self._llm.extract(safe_text)
        except TimeoutError as e:
            LLM_CALLS.labels("error").inc()
            raise LlmUnavailableError("extraction took too long") from e
        except LlmQuotaError:
            LLM_CALLS.labels("quota").inc()  # Gemini's own 429
            raise
        except Exception:
            LLM_CALLS.labels("error").inc()
            raise
        LLM_CALLS.labels("ok").inc()
        await self._cache.put(text_hash, self._llm.model, self._prompt_version, raw)
        log.info(
            "extract.llm_call",
            text_hash=text_hash[:12],
            model=self._llm.model,
            skills=len(raw.skills),
            used_today=self._budget.used_today,
        )
        result = self._map(raw, self._llm.model, cached=False)
        EXTRACTION_SECONDS.labels("false").observe(time.perf_counter() - start)
        return result

    def _map(self, raw: LlmExtraction, model: str, *, cached: bool) -> ExtractionResult:
        """Alias → slug, fuzzy fallback, de-duplicate (REQUIRED wins)."""
        by_slug: dict[str, ExtractedSkill] = {}
        unmapped: list[str] = []
        for s in raw.skills:
            slug = self.taxonomy.resolve(s.name, self._fuzzy_min_score)
            if slug is None:
                if s.name not in unmapped:
                    unmapped.append(s.name)
                continue
            current = by_slug.get(slug)
            if current is None or (
                current.importance == "PREFERRED" and s.importance == "REQUIRED"
            ):
                evidence = s.evidence[:EVIDENCE_MAX] if s.evidence else None
                by_slug[slug] = ExtractedSkill(
                    slug=slug, raw=s.name, importance=s.importance, evidence=evidence
                )
        return ExtractionResult(
            company=raw.company,
            title=raw.title,
            location=raw.location,
            seniority=raw.seniority,
            min_years=raw.min_years,
            skills=list(by_slug.values()),
            unmapped=unmapped,
            model=model,
            prompt_version=self._prompt_version,
            cached=cached,
        )
