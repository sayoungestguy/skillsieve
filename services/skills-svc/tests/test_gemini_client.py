"""GeminiClient against a stubbed SDK: retries, validation retry and error mapping."""

import asyncio
from typing import Any

import pytest
from google.genai import errors

from app.llm.client import GeminiClient, load_prompt
from app.llm.errors import LlmQuotaError, LlmUnavailableError
from tests.conftest import FIXTURES

GOOD = (FIXTURES / "backend_engineer.json").read_text()


class _Response:
    def __init__(self, text: str) -> None:
        self.text = text


class _Models:
    def __init__(self, outcomes: list[str | Exception]) -> None:
        self.outcomes = outcomes
        self.calls: list[dict[str, Any]] = []

    async def generate_content(self, **kwargs: Any) -> _Response:
        self.calls.append(kwargs)
        outcome = self.outcomes.pop(0)
        if isinstance(outcome, Exception):
            raise outcome
        return _Response(outcome)


def _client(outcomes: list[str | Exception]) -> tuple[GeminiClient, _Models]:
    gemini = GeminiClient("test-key", "gemini-test", "extract-v1", timeout_s=5)
    models = _Models(outcomes)
    gemini._client = type("C", (), {"aio": type("A", (), {"models": models})()})()
    return gemini, models


@pytest.fixture(autouse=True)
def _no_sleep(monkeypatch: pytest.MonkeyPatch) -> None:
    async def instant(_: float) -> None:
        return None

    monkeypatch.setattr(asyncio, "sleep", instant)


def _api_error(code: int) -> errors.APIError:
    return errors.APIError(code, {"error": {"message": "x", "status": "X"}})


async def test_should_send_schema_and_system_prompt() -> None:
    gemini, models = _client([GOOD])

    result = await gemini.extract("post")

    config = models.calls[0]["config"]
    assert config.temperature == 0
    assert config.response_mime_type == "application/json"
    assert config.system_instruction == load_prompt("extract-v1")
    assert models.calls[0]["model"] == "gemini-test"
    assert result.company == "Northwind Labs"


async def test_should_retry_server_errors_then_succeed() -> None:
    gemini, models = _client([_api_error(503), _api_error(500), GOOD])

    result = await gemini.extract("post")

    assert len(models.calls) == 3
    assert result.title == "Backend Engineer"


async def test_should_map_persistent_429_to_quota_error() -> None:
    gemini, _ = _client([_api_error(429)] * 4)

    with pytest.raises(LlmQuotaError):
        await gemini.extract("post")


async def test_should_not_retry_bad_request() -> None:
    gemini, models = _client([_api_error(400)])

    with pytest.raises(LlmUnavailableError):
        await gemini.extract("post")
    assert len(models.calls) == 1


async def test_should_retry_once_with_validation_error_then_fail() -> None:
    gemini, models = _client(['{"min_years": "lots"}', GOOD])
    assert (await gemini.extract("post")).company == "Northwind Labs"
    assert "previous answer was invalid" in models.calls[1]["contents"]

    gemini, _ = _client(['{"min_years": -1}', '{"skills": "no"}'])
    with pytest.raises(LlmUnavailableError):
        await gemini.extract("post")
