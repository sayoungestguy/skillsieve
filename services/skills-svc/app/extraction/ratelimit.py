"""In-process quota guard under the Gemini free tier (one replica in R1)."""

import asyncio
import time
from collections import deque
from collections.abc import Callable
from datetime import UTC, date, datetime
from zoneinfo import ZoneInfo

from app.llm.errors import LlmQuotaError

# Gemini daily quotas reset at midnight Pacific time.
QUOTA_TZ = ZoneInfo("America/Los_Angeles")
MINUTE_S = 60.0


class LlmBudget:
    def __init__(
        self,
        per_minute: int,
        per_day: int,
        clock: Callable[[], float] = time.monotonic,
        today: Callable[[], date] | None = None,
    ) -> None:
        self._per_minute = per_minute
        self._per_day = per_day
        self._clock = clock
        self._today = today or (lambda: datetime.now(UTC).astimezone(QUOTA_TZ).date())
        self._recent: deque[float] = deque()
        self._day = self._today()
        self._used_today = 0
        self._lock = asyncio.Lock()

    async def acquire(self) -> None:
        """Reserve one call or raise LlmQuotaError (core-api then keeps the job PENDING)."""
        async with self._lock:
            now = self._clock()
            while self._recent and now - self._recent[0] >= MINUTE_S:
                self._recent.popleft()
            if self._today() != self._day:
                self._day, self._used_today = self._today(), 0
            if self._used_today >= self._per_day:
                raise LlmQuotaError("daily LLM budget used up")
            if len(self._recent) >= self._per_minute:
                raise LlmQuotaError("per-minute LLM budget used up")
            self._recent.append(now)
            self._used_today += 1

    @property
    def used_today(self) -> int:
        return self._used_today
