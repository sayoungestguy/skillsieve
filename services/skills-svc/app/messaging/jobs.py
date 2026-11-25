"""Read-only access to core-api's job_post table (core-api owns the schema)."""

import uuid

import asyncpg


class JobTextRepository:
    def __init__(self, pool: asyncpg.Pool) -> None:
        self._pool = pool

    async def text_of(self, job_id: uuid.UUID) -> tuple[str, str] | None:
        row = await self._pool.fetchrow(
            "SELECT raw_text, text_hash FROM job_post WHERE id = $1", job_id
        )
        if row is None:
            return None
        return str(row["raw_text"]), str(row["text_hash"])
