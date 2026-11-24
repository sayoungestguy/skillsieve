"""API models (TECHNICAL_DESIGN §6). JSON uses camelCase to match core-api."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

Importance = Literal["REQUIRED", "PREFERRED"]
Seniority = Literal["JUNIOR", "MID", "SENIOR", "LEAD", "UNKNOWN"]


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ExtractRequest(CamelModel):
    text_hash: str = Field(pattern=r"^[0-9a-f]{64}$")
    text: str = Field(min_length=1, max_length=60_000)


class ExtractedSkill(CamelModel):
    slug: str
    raw: str
    importance: Importance
    evidence: str | None = Field(default=None, max_length=120)


class ExtractionResult(CamelModel):
    company: str | None
    title: str | None
    location: str | None
    seniority: Seniority
    min_years: int | None
    skills: list[ExtractedSkill]
    unmapped: list[str]
    model: str
    prompt_version: str
    cached: bool
