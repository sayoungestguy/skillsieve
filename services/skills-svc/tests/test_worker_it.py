"""Integration: real Postgres + RabbitMQ; the app's worker consumes job.created end to end."""

import asyncio
import json
import uuid
from collections.abc import AsyncIterator, Iterator
from datetime import UTC, datetime

import aio_pika
import asyncpg
import pytest
from fastapi.testclient import TestClient
from testcontainers.core.container import DockerContainer
from testcontainers.core.waiting_utils import wait_for_logs

from app import main
from app.config import get_settings
from app.llm.schema import LlmExtraction
from app.messaging.topology import EXCHANGE, EXTRACT_DLQ, JOB_CREATED
from tests.conftest import FakeLlm
from tests.test_postgres_it import _migrate_once, database_url  # noqa: F401  (shared fixture)

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def amqp_url() -> Iterator[str]:
    with DockerContainer("rabbitmq:4.1-alpine").with_exposed_ports(5672) as mq:
        wait_for_logs(mq, "Server startup complete", timeout=90)
        yield f"amqp://guest:guest@{mq.get_container_host_ip()}:{mq.get_exposed_port(5672)}/"


@pytest.fixture
def app_with_worker(
    database_url: str,  # noqa: F811
    amqp_url: str,
    monkeypatch: pytest.MonkeyPatch,
    fixture_result: LlmExtraction,
) -> Iterator[FakeLlm]:
    asyncio.run(_migrate_once(database_url))
    monkeypatch.setenv("DATABASE_URL", database_url)
    monkeypatch.setenv("AMQP_URL", amqp_url)
    get_settings.cache_clear()
    llm = FakeLlm(fixture_result)
    monkeypatch.setattr(main, "build_llm", lambda _settings: llm)
    with TestClient(main.create_app()):
        yield llm
    get_settings.cache_clear()


@pytest.fixture
async def results(amqp_url: str) -> AsyncIterator[aio_pika.abc.AbstractQueue]:
    """A private queue that sees every job.skills.* event (stands in for core-api)."""
    conn = await aio_pika.connect_robust(amqp_url)
    channel = await conn.channel()
    events = await channel.declare_exchange(EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
    queue = await channel.declare_queue(exclusive=True)
    await queue.bind(events, "job.skills.*")
    yield queue
    await conn.close()


async def _insert_job(dsn: str, text: str) -> tuple[uuid.UUID, str]:
    conn = await asyncpg.connect(dsn, server_settings={"search_path": "skillsieve"})
    try:
        user_id = await conn.fetchval(
            "INSERT INTO app_user (email, password_hash) VALUES ($1, 'x') RETURNING id",
            f"{uuid.uuid4()}@test.local",
        )
        text_hash = uuid.uuid4().hex * 2
        job_id = await conn.fetchval(
            "INSERT INTO job_post (user_id, raw_text, text_hash) VALUES ($1, $2, $3) RETURNING id",
            user_id,
            text,
            text_hash,
        )
        return job_id, text_hash
    finally:
        await conn.close()


async def _publish(amqp_url: str, body: bytes) -> None:
    conn = await aio_pika.connect_robust(amqp_url)
    try:
        channel = await conn.channel()
        events = await channel.get_exchange(EXCHANGE)
        await events.publish(aio_pika.Message(body), routing_key=JOB_CREATED)
    finally:
        await conn.close()


async def _next(queue: aio_pika.abc.AbstractQueue, timeout_s: float = 20) -> bytes:
    for _ in range(int(timeout_s / 0.2)):
        msg = await queue.get(no_ack=True, fail=False)
        if msg is not None:
            return msg.body
        await asyncio.sleep(0.2)
    raise AssertionError("no message")


async def test_should_extract_queued_job_and_publish_result(
    app_with_worker: FakeLlm,
    results: aio_pika.abc.AbstractQueue,
    database_url: str,  # noqa: F811
    amqp_url: str,
) -> None:
    job_id, text_hash = await _insert_job(database_url, "Backend Engineer, Java 21 and K8s")
    event_id = uuid.uuid4()
    await _publish(
        amqp_url,
        json.dumps(
            {
                "eventId": str(event_id),
                "type": "job.created",
                "jobId": str(job_id),
                "textHash": text_hash,
                "occurredAt": datetime.now(UTC).isoformat(),
            }
        ).encode(),
    )

    body = json.loads(await _next(results))

    assert body["type"] == "job.skills.extracted"
    assert body["jobId"] == str(job_id)
    assert body["causationId"] == str(event_id)
    assert "kubernetes" in {s["slug"] for s in body["result"]["skills"]}
    assert app_with_worker.calls == ["Backend Engineer, Java 21 and K8s"]


async def test_should_dead_letter_unreadable_message(
    app_with_worker: FakeLlm, amqp_url: str
) -> None:
    await _publish(amqp_url, b"not json")

    conn = await aio_pika.connect_robust(amqp_url)
    try:
        channel = await conn.channel()
        dlq = await channel.get_queue(EXTRACT_DLQ)
        assert await _next(dlq) == b"not json"
    finally:
        await conn.close()
    assert app_with_worker.calls == []
