"""Canonical skills and aliases, loaded from Postgres (seeded by core-api's Flyway)."""

import re
import string

from rapidfuzz import fuzz, process

_PUNCT = re.compile(f"[{re.escape(string.punctuation.replace('#', '').replace('+', ''))}]")
_SPACES = re.compile(r"\s+")


def normalise_term(raw: str) -> str:
    """Lower-case, drop punctuation (keep # and + for C#, C++), collapse spaces."""
    keep_dots = raw.lower().strip()
    if keep_dots in {"node.js", ".net", "ci/cd"}:
        return keep_dots
    return _SPACES.sub(" ", _PUNCT.sub(" ", keep_dots)).strip()


class Taxonomy:
    """alias (already lower-case) → slug."""

    def __init__(self, aliases: dict[str, str]) -> None:
        self.aliases = dict(aliases)
        normalised = {normalise_term(a): s for a, s in aliases.items()}
        self._lookup = {**normalised, **self.aliases}

    def resolve(self, raw: str, min_score: float) -> str | None:
        lookup = self._lookup
        term = raw.lower().strip()
        for candidate in (term, normalise_term(raw)):
            if candidate in lookup:
                return lookup[candidate]
        match = process.extractOne(
            normalise_term(raw), lookup.keys(), scorer=fuzz.ratio, score_cutoff=min_score
        )
        return lookup[match[0]] if match else None

    @property
    def size(self) -> int:
        return len(self.aliases)
