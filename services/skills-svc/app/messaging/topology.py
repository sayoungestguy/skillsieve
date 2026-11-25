"""RabbitMQ names shared with core-api (core-api `Messaging.java`). Change both sides together."""

from collections.abc import Mapping

from aio_pika.abc import FieldValue

EXCHANGE = "skillsieve.events"
DEAD_LETTER_EXCHANGE = "skillsieve.dlx"
JOB_CREATED = "job.created"
SKILLS_EXTRACTED = "job.skills.extracted"
SKILLS_FAILED = "job.skills.failed"
EXTRACT_QUEUE = "skills.extract"
EXTRACT_DLQ = "skills.extract.dlq"

# Same arguments core-api declares; RabbitMQ refuses a redeclare with different ones.
EXTRACT_QUEUE_ARGS: Mapping[str, FieldValue] = {
    "x-queue-type": "quorum",
    "x-dead-letter-exchange": DEAD_LETTER_EXCHANGE,
    "x-dead-letter-routing-key": EXTRACT_QUEUE,
}

# Retry ladder (TECHNICAL_DESIGN §14): one queue per delay so a long wait never blocks a short one.
# Each holds the message for its TTL, then dead-letters it back to skills.extract via job.created.
RETRY_DELAYS_S: tuple[int, ...] = (30, 120, 600)


def retry_queue(attempt: int) -> str:
    """Queue for retry number `attempt` (1-based)."""
    return f"{EXTRACT_QUEUE}.retry.{attempt}"
