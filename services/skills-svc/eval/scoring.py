"""Scores one extraction against its labels (TECHNICAL_DESIGN §7 evaluation)."""

from dataclasses import dataclass
from typing import Any

from app.extraction.models import ExtractionResult


@dataclass(frozen=True)
class PostScore:
    name: str
    true_pos: int
    false_pos: int
    false_neg: int
    importance_hits: int
    missing: list[str]
    extra: list[str]
    wrong_importance: list[str]
    fields_ok: int
    fields_total: int

    @property
    def precision(self) -> float:
        found = self.true_pos + self.false_pos
        return self.true_pos / found if found else 1.0

    @property
    def recall(self) -> float:
        wanted = self.true_pos + self.false_neg
        return self.true_pos / wanted if wanted else 1.0


def _same_text(expected: str | None, actual: str | None) -> bool:
    if expected is None:
        return actual is None
    return actual is not None and expected.lower() in actual.lower()


def score(name: str, expected: dict[str, Any], result: ExtractionResult) -> PostScore:
    want: dict[str, str] = expected["skills"]
    got = {s.slug: s.importance for s in result.skills}
    hits = sorted(set(want) & set(got))
    wrong = [s for s in hits if want[s] != got[s]]
    fields = [
        _same_text(expected["company"], result.company),
        _same_text(expected["title"], result.title),
        expected["seniority"] == result.seniority,
        expected["min_years"] == result.min_years,
    ]
    return PostScore(
        name=name,
        true_pos=len(hits),
        false_pos=len(set(got) - set(want)),
        false_neg=len(set(want) - set(got)),
        importance_hits=len(hits) - len(wrong),
        missing=sorted(set(want) - set(got)),
        extra=sorted(set(got) - set(want)),
        wrong_importance=wrong,
        fields_ok=sum(fields),
        fields_total=len(fields),
    )


@dataclass(frozen=True)
class Summary:
    precision: float
    recall: float
    importance_accuracy: float
    field_accuracy: float


def summarise(scores: list[PostScore]) -> Summary:
    tp = sum(s.true_pos for s in scores)
    fp = sum(s.false_pos for s in scores)
    fn = sum(s.false_neg for s in scores)
    return Summary(
        precision=tp / (tp + fp) if tp + fp else 1.0,
        recall=tp / (tp + fn) if tp + fn else 1.0,
        importance_accuracy=sum(s.importance_hits for s in scores) / tp if tp else 1.0,
        field_accuracy=sum(s.fields_ok for s in scores) / sum(s.fields_total for s in scores),
    )
