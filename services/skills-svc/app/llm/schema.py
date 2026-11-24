"""What we ask Gemini to return (structured output). Mapped to canonical skills afterwards."""

from typing import Literal

from pydantic import BaseModel, Field


class LlmSkill(BaseModel):
    name: str = Field(min_length=1, max_length=80)
    importance: Literal["REQUIRED", "PREFERRED"]
    evidence: str | None = None


class LlmExtraction(BaseModel):
    company: str | None = None
    title: str | None = None
    location: str | None = None
    seniority: Literal["JUNIOR", "MID", "SENIOR", "LEAD", "UNKNOWN"] = "UNKNOWN"
    min_years: int | None = Field(default=None, ge=0, le=40)
    skills: list[LlmSkill] = Field(default_factory=list, max_length=60)
