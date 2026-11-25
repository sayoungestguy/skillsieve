"""aio-pika transport around JobCreatedHandler: consume skills.extract, publish results, retry."""

import asyncio
import contextlib

import aio_pika
import structlog
from aio_pika.abc import (
    AbstractChannel,
    AbstractExchange,
    AbstractIncomingMessage,
    AbstractRobustConnection,
)

from app.messaging.handler import DeadLetter, Drop, JobCreatedHandler, Publish, Retry
from app.messaging.topology import (
    DEAD_LETTER_EXCHANGE,
    EXCHANGE,
    EXTRACT_DLQ,
    EXTRACT_QUEUE,
    EXTRACT_QUEUE_ARGS,
    JOB_CREATED,
    RETRY_DELAYS_S,
    retry_queue,
)

log = structlog.get_logger()
ATTEMPT_HEADER = "x-skillsieve-attempt"


async def declare(channel: AbstractChannel) -> tuple[AbstractExchange, aio_pika.abc.AbstractQueue]:
    """Idempotent: matches what core-api declares, plus our retry queues."""
    events = await channel.declare_exchange(EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True)
    dlx = await channel.declare_exchange(
        DEAD_LETTER_EXCHANGE, aio_pika.ExchangeType.DIRECT, durable=True
    )
    queue = await channel.declare_queue(
        EXTRACT_QUEUE, durable=True, arguments=dict(EXTRACT_QUEUE_ARGS)
    )
    await queue.bind(events, JOB_CREATED)
    dlq = await channel.declare_queue(EXTRACT_DLQ, durable=True)
    await dlq.bind(dlx, EXTRACT_QUEUE)
    for i, delay in enumerate(RETRY_DELAYS_S, start=1):
        await channel.declare_queue(
            retry_queue(i),
            durable=True,
            arguments={
                "x-message-ttl": delay * 1000,
                "x-dead-letter-exchange": EXCHANGE,
                "x-dead-letter-routing-key": JOB_CREATED,
            },
        )
    return events, queue


class ExtractionWorker:
    def __init__(self, url: str, handler: JobCreatedHandler) -> None:
        self._url = url
        self._handler = handler
        self._connection: AbstractRobustConnection | None = None

    async def start(self) -> None:
        # connect_robust reconnects and re-declares after a broker restart.
        self._connection = await aio_pika.connect_robust(self._url)
        channel = await self._connection.channel(publisher_confirms=True)
        await channel.set_qos(prefetch_count=1)  # one LLM call at a time (free-tier budget)
        events, queue = await declare(channel)

        async def on_message(message: AbstractIncomingMessage) -> None:
            await self._process(channel, events, message)

        await queue.consume(on_message)
        log.info("worker.started", queue=EXTRACT_QUEUE)

    async def stop(self) -> None:
        if self._connection is not None:
            await self._connection.close()

    async def _process(
        self, channel: AbstractChannel, events: AbstractExchange, message: AbstractIncomingMessage
    ) -> None:
        raw = message.headers.get(ATTEMPT_HEADER, 0)
        attempt = raw if isinstance(raw, int) else 0
        outcome = await self._handler.handle(message.body, attempt)
        match outcome:
            case Publish():
                await events.publish(_json(outcome.body), routing_key=outcome.routing_key)
                await message.ack()
            case Retry(attempt=n):
                retry = _json(message.body, message_id=message.message_id)
                retry.headers[ATTEMPT_HEADER] = n
                await channel.default_exchange.publish(retry, routing_key=retry_queue(n))
                await message.ack()
            case Drop():
                await message.ack()
            case DeadLetter(notify=notify):
                if notify is not None:
                    await events.publish(_json(notify.body), routing_key=notify.routing_key)
                await message.reject(requeue=False)


def _json(body: bytes, message_id: str | None = None) -> aio_pika.Message:
    return aio_pika.Message(
        body,
        content_type="application/json",
        delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
        message_id=message_id,
        headers={},
    )


async def run_until_cancelled(worker: ExtractionWorker, retry_s: float = 10.0) -> None:
    """Start the worker, retrying until the broker is reachable; stop on cancel."""
    try:
        while True:
            try:
                await worker.start()
                break
            except (OSError, aio_pika.exceptions.AMQPError) as e:
                log.warning("worker.broker_unreachable", error=type(e).__name__, retry_s=retry_s)
                await asyncio.sleep(retry_s)
        await asyncio.Event().wait()
    finally:
        with contextlib.suppress(Exception):
            await worker.stop()
