"""Privacy guard (CLAUDE.md C5): strip contact details before text leaves the cluster."""

import re

EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
# URLs with a query string can carry tracking ids or names; plain URLs are kept.
URL_WITH_QUERY = re.compile(r"https?://[^\s?#]+\?[^\s]*", re.IGNORECASE)
# +65 local numbers (8 digits, optional space/dash) and general international/long numbers.
PHONE_SG = re.compile(r"(?:\+65[\s-]?)?\b[689]\d{3}[\s-]?\d{4}\b")
PHONE_GENERAL = re.compile(r"\+\d[\d\s().-]{7,}\d")

REPLACEMENTS = (
    (EMAIL, "[email]"),
    (URL_WITH_QUERY, "[url]"),
    (PHONE_GENERAL, "[phone]"),
    (PHONE_SG, "[phone]"),
)


def redact(text: str) -> str:
    for pattern, token in REPLACEMENTS:
        text = pattern.sub(token, text)
    return text
