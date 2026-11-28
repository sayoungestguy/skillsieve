"""Local Ollama adapter for resume tailoring.

Resume text is personal data (CLAUDE.md C5). It goes only to a model we run ourselves: this module
talks to OLLAMA_URL and nothing else, and never imports the Gemini client.
"""

from typing import Protocol
from urllib.parse import urlparse

import httpx
import structlog
from pydantic import ValidationError

from app.llm.errors import LlmNotConfiguredError, LlmUnavailableError
from app.tailor.models import LlmTailoring

log = structlog.get_logger()

# Hosted LLM APIs that must never receive resume text, even by misconfiguration.
_CLOUD_HOSTS = ("googleapis.com", "openai.com", "anthropic.com", "ai.google.dev")


def check_local_url(url: str) -> str:
    host = (urlparse(url).hostname or "").lower()
    if not host or any(host == h or host.endswith("." + h) for h in _CLOUD_HOSTS):
        raise ValueError(f"OLLAMA_URL must point at a self-hosted Ollama, not {host or url!r}")
    return url.rstrip("/")


class TailorLlm(Protocol):
    model: str

    async def tailor(self, system: str, user: str) -> LlmTailoring: ...


class DisabledTailorLlm:
    """Used when OLLAMA_URL is empty."""

    def __init__(self, model: str) -> None:
        self.model = model

    async def tailor(self, system: str, user: str) -> LlmTailoring:
        raise LlmNotConfiguredError("OLLAMA_URL is not set")


class OllamaClient:
    def __init__(
        self, url: str, model: str, timeout_s: float, client: httpx.AsyncClient | None = None
    ) -> None:
        self.model = model
        self._url = check_local_url(url)
        self._client = client or httpx.AsyncClient(timeout=timeout_s)

    async def tailor(self, system: str, user: str) -> LlmTailoring:
        """One validated answer; asks again once if the JSON does not match the schema."""
        raw = await self._chat(system, user)
        try:
            return LlmTailoring.model_validate_json(raw)
        except ValidationError as e:
            log.warning("tailor.validation_retry", model=self.model, errors=e.error_count())
            hint = "\n\nYour previous answer did not match the JSON format. Answer again."
            try:
                return LlmTailoring.model_validate_json(await self._chat(system, user + hint))
            except ValidationError as again:
                raise LlmUnavailableError("model output failed validation twice") from again

    async def _chat(self, system: str, user: str) -> str:
        body = {
            "model": self.model,
            "stream": False,
            "format": LlmTailoring.model_json_schema(),
            "options": {"temperature": 0.2, "num_ctx": 4096, "num_predict": 700},
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        }
        try:
            res = await self._client.post(f"{self._url}/api/chat", json=body)
        except httpx.TimeoutException as e:
            raise LlmUnavailableError("Ollama timed out") from e
        except httpx.HTTPError as e:
            raise LlmUnavailableError(f"Ollama unreachable ({type(e).__name__})") from e
        if res.status_code != httpx.codes.OK:
            # The body may echo the prompt (resume text): log the status only.
            raise LlmUnavailableError(f"Ollama error {res.status_code}")
        content = res.json().get("message", {}).get("content")
        if not isinstance(content, str):
            raise LlmUnavailableError("Ollama returned no message")
        return content
