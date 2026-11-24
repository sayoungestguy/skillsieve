"""Record a real Gemini response as a test fixture (uses 1 request of quota).

See tests/fixtures/README.md.
"""

import asyncio
import sys
from pathlib import Path

from app.config import get_settings
from app.extraction.redact import redact
from app.main import build_llm


async def main(text: str) -> None:
    llm = build_llm(get_settings())
    result = await llm.extract(redact(text))
    sys.stdout.write(result.model_dump_json(indent=2) + "\n")


if __name__ == "__main__":
    asyncio.run(main(Path(sys.argv[1]).read_text(encoding="utf-8")))
