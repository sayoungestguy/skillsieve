import json
import uuid
from datetime import UTC, datetime

import pytest

from app.llm.errors import LlmQuotaError
from app.llm.schema import LlmExtraction
from app.messaging.handler import DeadLetter, Drop, JobCreatedHandler, Publish, Retry
from app.messaging.topology import RETRY_DELAYS_S, SKILLS_EXTRACTED, SKILLS_FAILED
from tests.conftest import FakeLlm, make_service

JOB_ID = uuid.uuid4()
EVENT_ID = uuid.uuid4()
HASH = "a" * 64
TEXT = "Backend Engineer at Northwind Labs. Java 21, Spring Boot, K8s."


class FakeJobs:
    def __init__(self, rows: dict[uuid.UUID, tuple[str, str]]) -> None:
        self.rows = rows

    async def text_of(self, job_id: uuid.UUID) -> tuple[str, str] | None:
        return self.rows.get(job_id)


def event(job_id: uuid.UUID = JOB_ID) -> bytes:
    return json.dumps(
        {
            "eventId": str(EVENT_ID),
            "type": "job.created",
            "jobId": str(job_id),
            "textHash": HASH,
            "occurredAt": datetime.now(UTC).isoformat(),
        }
    ).encode()


def handler(llm: FakeLlm) -> JobCreatedHandler:
    return JobCreatedHandler(make_service(llm), FakeJobs({JOB_ID: (TEXT, HASH)}))


async def test_should_publish_skills_when_extraction_works(fixture_result: LlmExtraction) -> None:
    llm = FakeLlm(fixture_result)

    out = await handler(llm).handle(event(), attempt=0)

    assert isinstance(out, Publish)
    assert out.routing_key == SKILLS_EXTRACTED
    body = json.loads(out.body)
    assert body["jobId"] == str(JOB_ID)
    assert body["causationId"] == str(EVENT_ID)
    assert body["eventId"] != str(EVENT_ID)
    assert {s["slug"] for s in body["result"]["skills"]} >= {"java", "kubernetes"}
    assert llm.calls == [TEXT]  # text came from the database, not the message


async def test_should_drop_when_job_was_deleted(fixture_result: LlmExtraction) -> None:
    llm = FakeLlm(fixture_result)

    out = await handler(llm).handle(event(uuid.uuid4()), attempt=0)

    assert isinstance(out, Drop)
    assert llm.calls == []


async def test_should_dead_letter_bad_message(fixture_result: LlmExtraction) -> None:
    out = await handler(FakeLlm(fixture_result)).handle(b'{"type":"nope"}', attempt=0)

    assert out == DeadLetter("bad message", notify=None)


@pytest.mark.parametrize("attempt", range(len(RETRY_DELAYS_S)))
async def test_should_retry_when_llm_is_busy(attempt: int) -> None:
    out = await handler(FakeLlm(LlmQuotaError("budget"))).handle(event(), attempt=attempt)

    assert out == Retry(attempt + 1)


async def test_should_give_up_and_notify_after_last_retry() -> None:
    out = await handler(FakeLlm(LlmQuotaError("budget"))).handle(
        event(), attempt=len(RETRY_DELAYS_S)
    )

    assert isinstance(out, DeadLetter)
    assert out.notify is not None
    assert out.notify.routing_key == SKILLS_FAILED
    body = json.loads(out.notify.body)
    assert body["retryLater"] is True
    assert body["reason"] == "LlmQuotaError"
    assert "result" not in body


async def test_should_not_retry_unexpected_errors() -> None:
    out = await handler(FakeLlm(RuntimeError("bug"))).handle(event(), attempt=0)

    assert isinstance(out, DeadLetter)
    assert out.notify is not None
    assert json.loads(out.notify.body)["retryLater"] is False
