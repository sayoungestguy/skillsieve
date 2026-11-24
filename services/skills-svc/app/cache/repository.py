"""extraction_cache: raw LLM output by (text_hash, prompt_version). Mapping re-runs on each read,
so taxonomy fixes apply to cached posts without spending quota."""

import asyncpg

from app.llm.schema import LlmExtraction

GET_SQL = """
SELECT model, result_json::text AS result FROM extraction_cache
WHERE text_hash = $1 AND prompt_version = $2
"""
PUT_SQL = """
INSERT INTO extraction_cache (text_hash, model, prompt_version, result_json)
VALUES ($1, $2, $3, $4::jsonb)
ON CONFLICT (text_hash) DO UPDATE
SET model = EXCLUDED.model, prompt_version = EXCLUDED.prompt_version,
    result_json = EXCLUDED.result_json, created_at = now()
"""


class CacheRepository:
    def __init__(self, pool: asyncpg.Pool) -> None:
        self._pool = pool

    async def get(self, text_hash: str, prompt_version: str) -> tuple[str, LlmExtraction] | None:
        row = await self._pool.fetchrow(GET_SQL, text_hash, prompt_version)
        if row is None:
            return None
        return row["model"], LlmExtraction.model_validate_json(row["result"])

    async def put(
        self, text_hash: str, model: str, prompt_version: str, result: LlmExtraction
    ) -> None:
        await self._pool.execute(
            PUT_SQL, text_hash, model, prompt_version, result.model_dump_json()
        )
