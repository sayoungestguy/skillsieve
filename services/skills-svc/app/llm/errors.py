class LlmError(Exception):
    """Base for LLM failures that core-api should treat as 'try later'."""


class LlmNotConfiguredError(LlmError):
    """No API key: extraction is switched off."""


class LlmQuotaError(LlmError):
    """Our own budget or Gemini's rate limit is used up."""


class LlmUnavailableError(LlmError):
    """Gemini failed after retries, or returned output we could not validate."""
