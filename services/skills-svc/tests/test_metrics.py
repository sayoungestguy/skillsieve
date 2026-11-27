import contextlib

from fastapi.testclient import TestClient
from prometheus_client import REGISTRY

from app.llm.errors import LlmQuotaError
from app.llm.schema import LlmExtraction
from tests.conftest import FakeLlm, MemoryCache, make_service


def value(name: str, **labels: str) -> float:
    return REGISTRY.get_sample_value(name, labels or None) or 0.0


async def test_should_count_llm_calls_cache_hits_and_duration(
    fixture_result: LlmExtraction,
) -> None:
    ok, hits, slow = (
        value("skillsieve_llm_calls_total", result="ok"),
        value("skillsieve_llm_cache_hits_total"),
        value("skillsieve_extraction_duration_seconds_count", cached="false"),
    )
    service = make_service(FakeLlm(fixture_result), MemoryCache())

    await service.extract("c" * 64, "Backend Engineer")
    await service.extract("c" * 64, "Backend Engineer")

    assert value("skillsieve_llm_calls_total", result="ok") == ok + 1
    assert value("skillsieve_llm_cache_hits_total") == hits + 1
    assert value("skillsieve_extraction_duration_seconds_count", cached="false") == slow + 1
    assert value("skillsieve_llm_calls_today") >= 1


async def test_should_count_quota_and_errors() -> None:
    quota, error = (
        value("skillsieve_llm_calls_total", result="quota"),
        value("skillsieve_llm_calls_total", result="error"),
    )

    for exc in (LlmQuotaError("429"), RuntimeError("boom")):
        with contextlib.suppress(Exception):
            await make_service(FakeLlm(exc)).extract("d" * 64, "text")

    assert value("skillsieve_llm_calls_total", result="quota") == quota + 1
    assert value("skillsieve_llm_calls_total", result="error") == error + 1


def test_should_serve_metrics_with_route_labels() -> None:
    from app.main import create_app

    client = TestClient(create_app())  # no lifespan: /healthz and /metrics need no DB
    client.get("/healthz")

    body = client.get("/metrics").text

    assert (
        'skillsieve_http_request_duration_seconds_count{method="GET",route="/healthz",status="200"}'
        in body
    )
    assert "skillsieve_llm_daily_limit" in body
