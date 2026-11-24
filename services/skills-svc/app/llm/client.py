"""Gemini adapter. The rest of the app only sees the LlmClient protocol (easy to fake in tests)."""

import asyncio
import random
from pathlib import Path
from typing import Protocol

import structlog
from google import genai
from google.genai import errors, types
from pydantic import ValidationError

from app.llm.errors import LlmNotConfiguredError, LlmQuotaError, LlmUnavailableError
from app.llm.schema import LlmExtraction

log = structlog.get_logger()

PROMPT_DIR = Path(__file__).parent / "prompts"
RETRYABLE = {429, 500, 502, 503, 504}
BACKOFF_S = (2.0, 4.0, 8.0)
TOO_MANY_REQUESTS = 429


def load_prompt(version: str) -> str:
    name = version.replace("-", "_") + ".txt"
    return (PROMPT_DIR / name).read_text(encoding="utf-8")


class LlmClient(Protocol):
    model: str

    async def extract(self, text: str) -> LlmExtraction: ...


class DisabledLlmClient:
    """Used when GEMINI_API_KEY is empty."""

    def __init__(self, model: str) -> None:
        self.model = model

    async def extract(self, text: str) -> LlmExtraction:
        raise LlmNotConfiguredError("GEMINI_API_KEY is not set")


class GeminiClient:
    def __init__(self, api_key: str, model: str, prompt_version: str, timeout_s: float) -> None:
        self.model = model
        self._client = genai.Client(api_key=api_key)
        self._system = load_prompt(prompt_version)
        self._timeout_s = timeout_s

    async def extract(self, text: str) -> LlmExtraction:
        """One validated extraction; retries 429/5xx with backoff and bad JSON once."""
        raw = await self._generate(text)
        try:
            return LlmExtraction.model_validate_json(raw)
        except ValidationError as e:
            log.warning("llm.validation_retry", model=self.model, errors=e.error_count())
            hint = f"\n\nYour previous answer was invalid: {e.errors(include_input=False)}"
            try:
                return LlmExtraction.model_validate_json(await self._generate(text + hint))
            except ValidationError as again:
                raise LlmUnavailableError("model output failed validation twice") from again

    async def _generate(self, text: str) -> str:
        config = types.GenerateContentConfig(
            system_instruction=self._system,
            response_mime_type="application/json",
            response_schema=LlmExtraction,
            temperature=0,
        )
        for attempt, delay in enumerate((*BACKOFF_S, None), start=1):
            try:
                response = await asyncio.wait_for(
                    self._client.aio.models.generate_content(
                        model=self.model, contents=text, config=config
                    ),
                    timeout=self._timeout_s,
                )
                return response.text or "{}"
            except errors.APIError as e:
                if e.code not in RETRYABLE or delay is None:
                    if e.code == TOO_MANY_REQUESTS:
                        raise LlmQuotaError("Gemini rate limit") from e
                    raise LlmUnavailableError(f"Gemini error {e.code}") from e
                log.warning("llm.retry", attempt=attempt, code=e.code)
                await asyncio.sleep(delay + random.uniform(0, 0.5))  # noqa: S311 - jitter
            except TimeoutError as e:
                raise LlmUnavailableError("Gemini timed out") from e
        raise LlmUnavailableError("unreachable")
