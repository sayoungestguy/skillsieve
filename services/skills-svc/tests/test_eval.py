"""Eval set: scorer unit tests, plus quality targets on recorded Gemini output (no network)."""

import pytest

from app.extraction.models import ExtractedSkill, ExtractionResult
from eval.pipeline import RECORDED, extract, load_post, load_taxonomy, post_names
from eval.run import PRECISION_TARGET, RECALL_TARGET
from eval.scoring import score, summarise


def _result(**skills: str) -> ExtractionResult:
    return ExtractionResult(
        company="Acme Pte Ltd",
        title="Senior Backend Engineer",
        location=None,
        seniority="SENIOR",
        min_years=5,
        skills=[ExtractedSkill(slug=k, raw=k, importance=v) for k, v in skills.items()],
        unmapped=[],
        model="m",
        prompt_version="extract-v1",
        cached=True,
    )


EXPECTED = {
    "company": "Acme",
    "title": "Backend Engineer",
    "seniority": "SENIOR",
    "min_years": 5,
    "skills": {"java": "REQUIRED", "kafka": "REQUIRED", "aws": "PREFERRED"},
}


def test_should_count_hits_misses_extras_and_importance() -> None:
    s = score("x", EXPECTED, _result(java="REQUIRED", kafka="PREFERRED", go="REQUIRED"))

    assert (s.true_pos, s.false_pos, s.false_neg) == (2, 1, 1)
    assert s.precision == pytest.approx(2 / 3)
    assert s.recall == pytest.approx(2 / 3)
    assert s.missing == ["aws"]
    assert s.extra == ["go"]
    assert s.wrong_importance == ["kafka"]
    assert s.fields_ok == 4  # company/title match as substrings, case-insensitive


def test_should_summarise_micro_averages() -> None:
    a = score("a", EXPECTED, _result(java="REQUIRED", kafka="REQUIRED", aws="PREFERRED"))
    b = score("b", EXPECTED, _result(java="REQUIRED"))

    total = summarise([a, b])
    assert total.precision == 1.0
    assert total.recall == pytest.approx(4 / 6)
    assert total.importance_accuracy == 1.0


def test_every_label_uses_a_known_skill() -> None:
    slugs = set(load_taxonomy().aliases.values())
    for name in post_names():
        _, expected = load_post(name)
        assert set(expected["skills"]) <= slugs, name
    assert len(post_names()) == 20


@pytest.mark.skipif(not RECORDED.exists(), reason="no recorded Gemini output yet")
async def test_recorded_extractions_meet_quality_targets() -> None:
    taxonomy = load_taxonomy()
    scores = []
    for name in post_names():
        text, expected = load_post(name)
        scores.append(score(name, expected, await extract(name, text, taxonomy=taxonomy)))

    total = summarise(scores)
    assert total.precision >= PRECISION_TARGET
    assert total.recall >= RECALL_TARGET
