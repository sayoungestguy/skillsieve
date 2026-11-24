import asyncpg

from app.taxonomy.taxonomy import Taxonomy

LOAD_SQL = """
SELECT a.alias, s.slug FROM skill_alias a JOIN skill s ON s.id = a.skill_id
"""


async def load_taxonomy(pool: asyncpg.Pool) -> Taxonomy:
    rows = await pool.fetch(LOAD_SQL)
    return Taxonomy({row["alias"]: row["slug"] for row in rows})
