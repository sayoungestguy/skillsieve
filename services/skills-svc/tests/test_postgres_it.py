"""Integration: real Postgres 16 with core-api's Flyway migrations, full app with lifespan."""

from collections.abc import Iterator
from pathlib import Path

import asyncpg
import pytest
from fastapi.testclient import TestClient
from testcontainers.postgres import PostgresContainer

from app import main
from app.config import get_settings
from app.llm.schema import LlmExtraction
from tests.conftest import FakeLlm, load_fixture

pytestmark = pytest.mark.integration

MIGRATIONS = Path(__file__).parents[2] / "core-api/src/main/resources/db/migration"
TOKEN = {"X-Internal-Token": "test-internal-token-0123456789"}


async def _migrate(dsn: str) -> None:
    conn = await asyncpg.connect(dsn)
    try:
        await conn.execute("CREATE SCHEMA skillsieve; SET search_path TO skillsieve;")
        for name in ("V1__init.sql", "R__seed_skills.sql"):
            await conn.execute((MIGRATIONS / name).read_text())
    finally:
        await conn.close()


@pytest.fixture(scope="module")
def database_url() -> Iterator[str]:
    with PostgresContainer("postgres:16-alpine", driver=None) as pg:
        yield pg.get_connection_url()


@pytest.fixture
def client(
    database_url: str, monkeypatch: pytest.MonkeyPatch, fixture_result: LlmExtraction
) -> Iterator[tuple[TestClient, FakeLlm]]:
    import asyncio

    asyncio.run(_migrate_once(database_url))
    monkeypatch.setenv("DATABASE_URL", database_url)
    get_settings.cache_clear()
    llm = FakeLlm(fixture_result)
    monkeypatch.setattr(main, "build_llm", lambda _settings: llm)
    with TestClient(main.create_app()) as c:
        yield c, llm
    get_settings.cache_clear()


_migrated: set[str] = set()


async def _migrate_once(dsn: str) -> None:
    if dsn not in _migrated:
        await _migrate(dsn)
        _migrated.add(dsn)


def test_should_be_ready_with_seeded_taxonomy(client: tuple[TestClient, FakeLlm]) -> None:
    c, _ = client
    res = c.get("/readyz")

    assert res.status_code == 200
    assert res.json()["skills"] > 100  # slugs, names and aliases from skills.yaml


def test_should_cache_in_postgres_and_map_with_real_taxonomy(
    client: tuple[TestClient, FakeLlm],
) -> None:
    c, llm = client
    body = {"textHash": "e" * 64, "text": "Backend Engineer at Northwind Labs"}

    first = c.post("/v1/extract", json=body, headers=TOKEN).json()
    second = c.post("/v1/extract", json=body, headers=TOKEN).json()

    assert len(llm.calls) == 1
    assert first["cached"] is False
    assert second["cached"] is True
    slugs = {s["slug"] for s in second["skills"]}
    assert {"java", "spring-boot", "kubernetes", "postgresql", "cicd", "elasticsearch"} <= slugs
    assert second["unmapped"] == ["Quantum Basket Weaving"]


def test_fixture_matches_llm_schema() -> None:
    assert load_fixture("backend_engineer").skills
