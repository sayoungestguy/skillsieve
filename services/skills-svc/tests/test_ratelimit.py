from datetime import date

import pytest

from app.extraction.ratelimit import LlmBudget
from app.llm.errors import LlmQuotaError


async def test_should_enforce_per_minute_then_recover() -> None:
    now = [0.0]
    budget = LlmBudget(per_minute=2, per_day=100, clock=lambda: now[0])
    await budget.acquire()
    await budget.acquire()

    with pytest.raises(LlmQuotaError):
        await budget.acquire()

    now[0] = 61.0
    await budget.acquire()
    assert budget.used_today == 3


async def test_should_enforce_daily_budget_and_reset_next_day() -> None:
    day = [date(2026, 11, 24)]
    now = [0.0]
    budget = LlmBudget(per_minute=100, per_day=1, clock=lambda: now[0], today=lambda: day[0])
    await budget.acquire()

    with pytest.raises(LlmQuotaError, match="daily"):
        await budget.acquire()

    day[0] = date(2026, 11, 25)
    await budget.acquire()
