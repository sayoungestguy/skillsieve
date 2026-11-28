"""Turns a resume and a job's skills into 3-5 rewritten bullets, using the local model only."""

import re
import time

import structlog

from app.llm.errors import LlmError, LlmUnavailableError
from app.metrics import TAILOR_CALLS, TAILOR_SECONDS
from app.tailor.models import LlmSuggestion, Suggestion, TailorRequest, TailorResult
from app.tailor.ollama import TailorLlm
from app.tailor.prompts import PROMPT_VERSION, SYSTEM, build_user_message, bullets

log = structlog.get_logger()
MAX_SUGGESTIONS = 5
RETRY_HINT = (
    "\n\nYour last answer added skills or tools that are not in the bullets. Rewrite only "
    "with what each bullet already says."
)


class NoBulletsError(ValueError):
    """The resume has no lines long enough to rewrite."""


def _mentions(text: str, skill: str) -> bool:
    return re.search(rf"(?<![\w]){re.escape(skill)}(?![\w])", text, re.IGNORECASE) is not None


class TailorService:
    def __init__(self, llm: TailorLlm) -> None:
        self.llm = llm

    async def tailor(self, req: TailorRequest) -> TailorResult:
        numbered = bullets(req.resume_text)
        if not numbered:
            raise NoBulletsError("no resume lines to rewrite")
        start = time.perf_counter()
        user = build_user_message(req, numbered)
        proposed = 0
        suggestions: list[Suggestion] = []
        try:
            for attempt in (1, 2):  # small models sometimes only invent; ask once more
                out = await self.llm.tailor(SYSTEM, user if attempt == 1 else user + RETRY_HINT)
                proposed = len(out.suggestions)
                suggestions = self._ground(req, numbered, out.suggestions)
                if suggestions:
                    break
                log.warning("tailor.all_rejected", attempt=attempt, proposed=proposed)
        except LlmError:
            TAILOR_CALLS.labels("error").inc()
            raise
        finally:
            TAILOR_SECONDS.observe(time.perf_counter() - start)
        if not suggestions:
            TAILOR_CALLS.labels("rejected").inc()
            raise LlmUnavailableError("the model gave no usable suggestions; try again")
        TAILOR_CALLS.labels("ok").inc()
        # Counts only: the resume text is never logged.
        log.info(
            "tailor.done",
            model=self.llm.model,
            bullets=len(numbered),
            proposed=proposed,
            kept=len(suggestions),
            seconds=round(time.perf_counter() - start, 1),
        )
        return TailorResult(
            model=self.llm.model, prompt_version=PROMPT_VERSION, suggestions=suggestions
        )

    @staticmethod
    def _ground(
        req: TailorRequest, numbered: list[str], proposed: list[LlmSuggestion]
    ) -> list[Suggestion]:
        """Keep rewrites of real bullets that claim no skill the resume lacks."""
        known = {s.name.casefold(): s.name for s in req.skills}
        resume = req.resume_text
        # Job skills the resume does not show: core-api's gap list plus any job skill absent here.
        missing = {m for m in req.missing_skills} | {
            s.name for s in req.skills if not _mentions(resume, s.name)
        }
        kept: list[Suggestion] = []
        used: set[int] = set()
        for s in proposed:
            if not 1 <= s.bullet <= len(numbered) or s.bullet in used:
                continue
            original = numbered[s.bullet - 1]
            text = s.suggested.strip()
            claims = [m for m in missing if _mentions(text, m) and not _mentions(original, m)]
            if claims or text.casefold() == original.casefold():
                continue
            used.add(s.bullet)
            skills = [known[x.casefold()] for x in s.skills if x.casefold() in known]
            skills = [k for k in dict.fromkeys(skills) if _mentions(text, k)]
            kept.append(
                Suggestion(
                    original=original[:400],
                    suggested=text[:400],
                    skills=skills[:8],
                    reason=s.reason.strip()[:300],
                )
            )
            if len(kept) == MAX_SUGGESTIONS:
                break
        return kept
