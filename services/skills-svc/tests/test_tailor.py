"""Resume tailoring: local Ollama only, validated output, nothing sent to Gemini."""

import ast
import json
from pathlib import Path
from typing import Any

import httpx
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.routes import router
from app.llm.errors import LlmNotConfiguredError, LlmUnavailableError
from app.tailor.models import JobSkill, TailorRequest
from app.tailor.ollama import DisabledTailorLlm, OllamaClient, check_local_url
from app.tailor.prompts import bullets
from app.tailor.service import NoBulletsError, TailorService

TOKEN = {"X-Internal-Token": "test-internal-token-0123456789"}
RESUME = (
    "Jane Doe\n"
    "- Built data pipelines in Python processing 2M events/day\n"
    "- Wrote REST APIs for the billing team\n"
    "short line\n"
)
REQ = TailorRequest(
    resume_text=RESUME,
    job_title="Data Engineer",
    company="Acme",
    skills=[
        JobSkill(name="Kafka", importance="REQUIRED"),
        JobSkill(name="Python", importance="REQUIRED"),
        JobSkill(name="Airflow", importance="PREFERRED"),
    ],
    missing_skills=["Kafka", "Airflow"],
)
GOOD = {
    "suggestions": [
        {
            "bullet": 1,
            "suggested": "Built Python data pipelines handling 2M events per day",
            "skills": ["python", "Rust", "Kafka"],
            "reason": "Shows Python at scale.",
        },
        {
            "bullet": 2,
            "suggested": "Designed REST APIs serving the billing team",
            "skills": [],
            "reason": "Stronger verb.",
        },
    ]
}


def ollama(replies: list[Any], seen: list[dict[str, Any]] | None = None) -> OllamaClient:
    """Fake Ollama: each reply is JSON content (dict or str) or an HTTP error status (int)."""
    queue = list(replies)

    def handle(request: httpx.Request) -> httpx.Response:
        if seen is not None:
            seen.append({"url": str(request.url), "body": json.loads(request.content)})
        reply = queue.pop(0)
        if isinstance(reply, int):
            return httpx.Response(reply, json={"error": "boom"})
        content = reply if isinstance(reply, str) else json.dumps(reply)
        return httpx.Response(200, json={"message": {"role": "assistant", "content": content}})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handle))
    return OllamaClient("http://skillsieve-ollama:11434/", "qwen2.5:1.5b", 5.0, client=client)


async def test_should_send_resume_only_to_ollama_with_schema() -> None:
    seen: list[dict[str, Any]] = []
    result = await TailorService(ollama([GOOD], seen)).tailor(REQ)

    assert [s["url"] for s in seen] == ["http://skillsieve-ollama:11434/api/chat"]
    body = seen[0]["body"]
    assert body["model"] == "qwen2.5:1.5b"
    assert body["stream"] is False
    assert body["format"]["type"] == "object"
    user = body["messages"][1]["content"]
    assert "1. Built data pipelines in Python processing 2M events/day" in user
    assert "2. Wrote REST APIs for the billing team" in user
    assert "short line" not in user
    assert "Jane Doe" not in user
    assert "Required skills: Kafka, Python" in user
    assert result.prompt_version == "tailor-v2"
    assert len(result.suggestions) == 2


async def test_should_fill_original_and_keep_only_shown_job_skills() -> None:
    result = await TailorService(ollama([GOOD])).tailor(REQ)

    first, second = result.suggestions
    assert first.original == "Built data pipelines in Python processing 2M events/day"
    assert first.skills == ["Python"]  # Rust: not a job skill; Kafka: not in the bullet
    assert second.original == "Wrote REST APIs for the billing team"


async def test_should_drop_rewrites_that_claim_missing_skills() -> None:
    invented = {
        "suggestions": [
            {"bullet": 1, "suggested": "Built Kafka pipelines in Python at 2M events/day"},
            {"bullet": 2, "suggested": "Orchestrated REST APIs with Airflow"},
            {"bullet": 2, "suggested": "Designed REST APIs serving the billing team"},
        ]
    }

    result = await TailorService(ollama([invented])).tailor(REQ)

    assert [s.suggested for s in result.suggestions] == [
        "Designed REST APIs serving the billing team"
    ]


async def test_should_drop_bad_numbers_duplicates_and_unchanged_bullets() -> None:
    noisy = {
        "suggestions": [
            {"bullet": 9, "suggested": "Out of range"},
            {"bullet": 2, "suggested": "wrote rest apis for the billing team"},
            {"bullet": 1, "suggested": "Built Python pipelines at 2M events/day"},
            {"bullet": 1, "suggested": "Second rewrite of the same bullet"},
        ]
    }

    result = await TailorService(ollama([noisy])).tailor(REQ)

    assert [s.suggested for s in result.suggestions] == ["Built Python pipelines at 2M events/day"]


async def test_should_ask_again_once_when_everything_is_invented() -> None:
    bad = {"suggestions": [{"bullet": 1, "suggested": "Ran Kafka clusters"}]}
    seen: list[dict[str, Any]] = []

    result = await TailorService(ollama([bad, GOOD], seen)).tailor(REQ)

    assert len(result.suggestions) == 2
    assert "not in the bullets" in seen[1]["body"]["messages"][1]["content"]
    with pytest.raises(LlmUnavailableError, match="no usable suggestions"):
        await TailorService(ollama([bad, bad])).tailor(REQ)


def test_should_prefer_list_items_over_headings() -> None:
    cv = "Software Engineer, 2.5 years at Acme\n- Built REST APIs in Java for bookings\n"
    assert bullets(cv) == ["Built REST APIs in Java for bookings"]
    plain = "Software Engineer, 2.5 years at Acme\nshort"
    assert bullets(plain) == ["Software Engineer, 2.5 years at Acme"]


async def test_should_reject_resume_without_bullets() -> None:
    req = REQ.model_copy(update={"resume_text": "Jane\nshort"})

    with pytest.raises(NoBulletsError):
        await TailorService(ollama([GOOD])).tailor(req)


async def test_should_retry_bad_json_once_then_fail() -> None:
    ok = await TailorService(ollama(["not json", GOOD])).tailor(REQ)
    assert len(ok.suggestions) == 2

    with pytest.raises(LlmUnavailableError, match="validation twice"):
        await TailorService(ollama(["nope", {"suggestions": []}])).tailor(REQ)


async def test_should_map_http_errors_to_unavailable() -> None:
    with pytest.raises(LlmUnavailableError, match="Ollama error 500"):
        await TailorService(ollama([500])).tailor(REQ)


async def test_should_be_off_without_url() -> None:
    with pytest.raises(LlmNotConfiguredError):
        await TailorService(DisabledTailorLlm("m")).tailor(REQ)


@pytest.mark.parametrize(
    "url",
    [
        "https://generativelanguage.googleapis.com/v1",
        "https://api.openai.com",
        "https://api.anthropic.com",
        "not a url",
    ],
)
def test_should_refuse_cloud_llm_urls(url: str) -> None:
    with pytest.raises(ValueError, match="self-hosted"):
        check_local_url(url)


def test_should_accept_in_cluster_and_host_urls() -> None:
    assert check_local_url("http://skillsieve-ollama:11434/") == "http://skillsieve-ollama:11434"
    assert check_local_url("http://host.k3d.internal:11434") == "http://host.k3d.internal:11434"


def test_tailor_package_never_imports_gemini() -> None:
    """Static guard for CLAUDE.md C5: no code path from resume text to the Gemini client."""
    banned = ("google", "app.llm.client")
    for path in Path("app/tailor").glob("*.py"):
        tree = ast.parse(path.read_text())
        for node in ast.walk(tree):
            names = []
            if isinstance(node, ast.Import):
                names = [a.name for a in node.names]
            elif isinstance(node, ast.ImportFrom) and node.module:
                names = [node.module]
            for name in names:
                assert not name.startswith(banned), f"{path} imports {name}"


def _app(service: TailorService) -> FastAPI:
    app = FastAPI()
    app.include_router(router)
    app.state.tailor = service
    return app


def _body() -> dict[str, Any]:
    return REQ.model_dump(by_alias=True)


def test_route_should_return_camel_case() -> None:
    with TestClient(_app(TailorService(ollama([GOOD])))) as c:
        res = c.post("/v1/tailor", json=_body(), headers=TOKEN)

    assert res.status_code == 200
    data = res.json()
    assert data["promptVersion"] == "tailor-v2"
    assert data["suggestions"][0]["original"].startswith("Built data pipelines")


def test_route_should_need_token_and_valid_body() -> None:
    with TestClient(_app(TailorService(ollama([GOOD])))) as c:
        assert c.post("/v1/tailor", json=_body()).status_code == 401
        assert (
            c.post("/v1/tailor", json={**_body(), "skills": []}, headers=TOKEN).status_code == 422
        )
        big = {**_body(), "resumeText": "x" * 20_001}
        assert c.post("/v1/tailor", json=big, headers=TOKEN).status_code == 422
        none = {**_body(), "resumeText": "too short"}
        assert c.post("/v1/tailor", json=none, headers=TOKEN).status_code == 422
        big = {**_body(), "resumeText": "x" * 20_001}
        assert c.post("/v1/tailor", json=big, headers=TOKEN).status_code == 422


def test_route_should_503_when_tailoring_off() -> None:
    with TestClient(_app(TailorService(DisabledTailorLlm("m")))) as c:
        res = c.post("/v1/tailor", json=_body(), headers=TOKEN)

    assert res.status_code == 503
    assert "OLLAMA_URL" in res.json()["detail"]
