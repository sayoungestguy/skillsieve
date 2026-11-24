import pytest

from app.taxonomy.taxonomy import normalise_term
from tests.conftest import TAXONOMY


@pytest.mark.parametrize(
    ("raw", "slug"),
    [
        ("Kubernetes", "kubernetes"),
        ("K8s", "kubernetes"),
        ("  Postgres ", "postgresql"),
        ("Spring-Boot", "spring-boot"),
        ("C#", "csharp"),
        ("Node.js", "nodejs"),
        ("Kubernetis", "kubernetes"),  # fuzzy (typo)
        ("Elasticsearch.", "elasticsearch"),
    ],
)
def test_should_resolve_aliases_and_close_spellings(raw: str, slug: str) -> None:
    assert TAXONOMY.resolve(raw, min_score=90) == slug


def test_should_return_none_for_unknown_skill() -> None:
    assert TAXONOMY.resolve("Quantum Basket Weaving", min_score=92) is None


def test_should_normalise_punctuation_but_keep_special_names() -> None:
    assert normalise_term("Spring-Boot!") == "spring boot"
    assert normalise_term("CI/CD") == "ci/cd"
