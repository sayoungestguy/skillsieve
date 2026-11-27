"""Turns one `job.created` event into an outcome. No broker code here, so it is easy to test."""

import uuid
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Literal, Protocol

import structlog
from pydantic import ValidationError

from app.extraction.models import CamelModel, ExtractionResult
from app.extraction.service import ExtractionService
from app.llm.errors import LlmError
from app.messaging.topology import RETRY_DELAYS_S, SKILLS_EXTRACTED, SKILLS_FAILED

log = structlog.get_logger()


class JobCreated(CamelModel):
    """core-api outbox payload. The text is not in the message; we read it by id."""

    event_id: uuid.UUID
    type: Literal["job.created"]
    job_id: uuid.UUID
    text_hash: str
    occurred_at: datetime


class JobTexts(Protocol):
    async def text_of(self, job_id: uuid.UUID) -> tuple[str, str] | None:
        """(raw_text, text_hash), or None when the job was deleted."""
        ...


@dataclass(frozen=True)
class Publish:
    """Send `body` to the events exchange with this routing key, then ack."""

    routing_key: str
    body: bytes


@dataclass(frozen=True)
class Retry:
    """Park the message in the retry queue for attempt `attempt`, then ack."""

    attempt: int


@dataclass(frozen=True)
class Drop:
    """Ack and forget (job is gone)."""

    reason: str


@dataclass(frozen=True)
class DeadLetter:
    """Reject without requeue → skills.extract.dlq. `notify` is sent first so the UI can move on."""

    reason: str
    notify: Publish | None


Outcome = Publish | Retry | Drop | DeadLetter


class ResultEvent(CamelModel):
    event_id: uuid.UUID
    type: str
    causation_id: uuid.UUID
    job_id: uuid.UUID
    text_hash: str
    occurred_at: datetime
    result: ExtractionResult | None = None
    retry_later: bool | None = None
    reason: str | None = None


class JobCreatedHandler:
    def __init__(self, service: ExtractionService, jobs: JobTexts) -> None:
        self._service = service
        self._jobs = jobs

    async def handle(self, body: bytes, attempt: int) -> Outcome:
        """`attempt` is 0 for the first delivery, then 1, 2, … after each retry."""
        try:
            event = JobCreated.model_validate_json(body)
        except ValidationError:
            log.warning("worker.bad_message", size=len(body))
            return DeadLetter("bad message", notify=None)

        found = await self._jobs.text_of(event.job_id)
        if found is None:
            log.info("worker.job_gone", job_id=str(event.job_id))
            return Drop("job deleted")
        text, text_hash = found  # the database wins over the hash in the message

        try:
            result = await self._service.extract(text_hash, text)
        except LlmError as e:
            if attempt < len(RETRY_DELAYS_S):
                log.info(
                    "worker.retry",
                    job_id=str(event.job_id),
                    attempt=attempt + 1,
                    error=type(e).__name__,
                )
                return Retry(attempt + 1)
            log.warning("worker.gave_up", job_id=str(event.job_id), error=type(e).__name__)
            return DeadLetter(
                "retries used up", notify=self._failed(event, text_hash, True, type(e).__name__)
            )
        except Exception as e:  # a bug or bad data: retrying will not help
            log.exception("worker.error", job_id=str(event.job_id))
            return DeadLetter(
                "unexpected error", notify=self._failed(event, text_hash, False, type(e).__name__)
            )

        log.info(
            "worker.done", job_id=str(event.job_id), skills=len(result.skills), cached=result.cached
        )
        return Publish(
            SKILLS_EXTRACTED, self._event(event, text_hash, SKILLS_EXTRACTED, result=result)
        )

    def _failed(self, event: JobCreated, text_hash: str, retry_later: bool, reason: str) -> Publish:
        body = self._event(event, text_hash, SKILLS_FAILED, retry_later=retry_later, reason=reason)
        return Publish(SKILLS_FAILED, body)

    @staticmethod
    def _event(event: JobCreated, text_hash: str, kind: str, **extra: object) -> bytes:
        out = ResultEvent(
            event_id=uuid.uuid4(),
            type=kind,
            causation_id=event.event_id,
            job_id=event.job_id,
            text_hash=text_hash,
            occurred_at=datetime.now(UTC),
            **extra,
        )
        return out.model_dump_json(by_alias=True, exclude_none=True).encode()
