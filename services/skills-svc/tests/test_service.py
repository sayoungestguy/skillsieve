import pytest

from app.llm.errors import LlmQuotaError, LlmUnavailableError
from app.llm.schema import LlmExtraction
from tests.conftest import FakeLlm, MemoryCache, make_service

HASH = "a" * 64


async def test_should_map_aliases_dedupe_and_report_unmapped(
    fixture_result: LlmExtraction,
) -> None:
    service = make_service(FakeLlm(fixture_result))

    result = await service.extract(HASH, "post text")

    by_slug = {s.slug: s for s in result.skills}
    assert set(by_slug) == {
        "java", "spring-boot", "kubernetes", "postgresql", "kafka", "docker", "cicd",
        "terraform", "aws", "elasticsearch",
    }  # fmt: skip
    assert by_slug["kubernetes"].importance == "REQUIRED"  # listed as both: REQUIRED wins
    assert by_slug["kubernetes"].raw == "K8s"
    assert by_slug["aws"].importance == "PREFERRED"
    assert result.unmapped == ["Quantum Basket Weaving"]
    assert (result.company, result.title, result.seniority, result.min_years) == (
        "Northwind Labs", "Backend Engineer", "MID", 3,
    )  # fmt: skip
    assert result.cached is False
    assert result.model == "fake-flash-lite"


async def test_should_call_llm_once_per_text_hash(fixture_result: LlmExtraction) -> None:
    llm = FakeLlm(fixture_result)
    service = make_service(llm, MemoryCache())

    first = await service.extract(HASH, "post text")
    second = await service.extract(HASH, "post text")

    assert len(llm.calls) == 1
    assert first.cached is False
    assert second.cached is True
    assert second.skills == first.skills


async def test_should_redact_and_truncate_before_sending(fixture_result: LlmExtraction) -> None:
    llm = FakeLlm(fixture_result)
    service = make_service(llm)

    await service.extract(HASH, "Email hr@example.com. " + "x" * 20_000)

    assert "hr@example.com" not in llm.calls[0]
    assert len(llm.calls[0]) == 12_000


async def test_should_not_cache_failures() -> None:
    cache = MemoryCache()
    service = make_service(FakeLlm(LlmUnavailableError("boom")), cache)

    with pytest.raises(LlmUnavailableError):
        await service.extract(HASH, "post text")
    assert cache.rows == {}


async def test_should_refuse_when_budget_is_used_up(fixture_result: LlmExtraction) -> None:
    service = make_service(FakeLlm(fixture_result), per_minute=1)
    await service.extract("b" * 64, "one")

    with pytest.raises(LlmQuotaError):
        await service.extract("c" * 64, "two")
