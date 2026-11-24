"""HTTP layer with the service swapped for fakes (no DB, no lifespan)."""

from collections.abc import Iterator

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.routes import router
from app.llm.errors import LlmNotConfiguredError, LlmQuotaError
from app.llm.schema import LlmExtraction
from tests.conftest import FakeLlm, make_service

TOKEN = {"X-Internal-Token": "test-internal-token-0123456789"}
BODY = {"textHash": "d" * 64, "text": "Backend Engineer, Java and Kubernetes"}


def _app(llm: FakeLlm) -> FastAPI:
    app = FastAPI()
    app.include_router(router)
    app.state.service = make_service(llm)
    return app


@pytest.fixture
def client(fixture_result: LlmExtraction) -> Iterator[TestClient]:
    with TestClient(_app(FakeLlm(fixture_result))) as c:
        yield c


def test_should_return_camel_case_result(client: TestClient) -> None:
    res = client.post("/v1/extract", json=BODY, headers=TOKEN)

    assert res.status_code == 200
    data = res.json()
    assert data["minYears"] == 3
    assert data["promptVersion"] == "extract-v1"
    assert {"slug": "kafka", "raw": "Kafka", "importance": "REQUIRED"}.items() <= next(
        s for s in data["skills"] if s["slug"] == "kafka"
    ).items()


@pytest.mark.parametrize("headers", [{}, {"X-Internal-Token": "wrong-token-but-long-enough"}])
def test_should_reject_missing_or_wrong_token(client: TestClient, headers: dict[str, str]) -> None:
    assert client.post("/v1/extract", json=BODY, headers=headers).status_code == 401


def test_should_validate_hash(client: TestClient) -> None:
    res = client.post("/v1/extract", json={**BODY, "textHash": "nope"}, headers=TOKEN)
    assert res.status_code == 422


@pytest.mark.parametrize(
    ("error", "status"), [(LlmQuotaError("q"), 429), (LlmNotConfiguredError("k"), 503)]
)
def test_should_map_llm_errors(error: Exception, status: int) -> None:
    with TestClient(_app(FakeLlm(error))) as c:
        assert c.post("/v1/extract", json=BODY, headers=TOKEN).status_code == status


def test_should_report_health_and_not_ready_without_service() -> None:
    app = FastAPI()
    app.include_router(router)
    with TestClient(app) as c:
        assert c.get("/healthz").json() == {"status": "ok"}
        assert c.get("/readyz").status_code == 503
