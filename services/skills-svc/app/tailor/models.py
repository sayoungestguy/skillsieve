"""Resume tailoring API and model-output schemas (TECHNICAL_DESIGN §6). camelCase JSON."""

from pydantic import BaseModel, Field

from app.extraction.models import CamelModel, Importance


class JobSkill(CamelModel):
    name: str = Field(min_length=1, max_length=80)
    importance: Importance


class TailorRequest(CamelModel):
    resume_text: str = Field(min_length=1, max_length=20_000)
    job_title: str | None = Field(default=None, max_length=200)
    company: str | None = Field(default=None, max_length=200)
    skills: list[JobSkill] = Field(min_length=1, max_length=60)
    missing_skills: list[str] = Field(default_factory=list, max_length=60)


class Suggestion(CamelModel):
    original: str | None = Field(default=None, max_length=400)
    suggested: str = Field(min_length=1, max_length=400)
    skills: list[str] = Field(default_factory=list, max_length=8)
    reason: str = Field(default="", max_length=300)


class TailorResult(CamelModel):
    model: str
    prompt_version: str
    suggestions: list[Suggestion]


class LlmSuggestion(BaseModel):
    """What we ask the local model for. It names a bullet by number; we copy the text ourselves."""

    bullet: int = Field(ge=1, le=200)
    suggested: str = Field(min_length=1)
    skills: list[str] = Field(default_factory=list)
    reason: str = ""


class LlmTailoring(BaseModel):
    suggestions: list[LlmSuggestion] = Field(min_length=1, max_length=10)
