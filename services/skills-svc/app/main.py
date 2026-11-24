"""skills-svc: internal API that turns job post text into canonical skills."""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import asyncpg
import structlog
from fastapi import FastAPI

from app.api.routes import router
from app.cache.repository import CacheRepository
from app.config import Settings, get_settings
from app.extraction.ratelimit import LlmBudget
from app.extraction.service import ExtractionService
from app.llm.client import DisabledLlmClient, GeminiClient, LlmClient
from app.logging import configure_logging
from app.taxonomy.repository import load_taxonomy

log = structlog.get_logger()


def build_llm(settings: Settings) -> LlmClient:
    if settings.gemini_api_key is None or not settings.gemini_api_key.get_secret_value():
        log.warning("llm.disabled", reason="GEMINI_API_KEY not set")
        return DisabledLlmClient(settings.gemini_model)
    return GeminiClient(
        settings.gemini_api_key.get_secret_value(),
        settings.gemini_model,
        settings.prompt_version,
        settings.llm_timeout_s,
    )


async def open_pool(settings: Settings) -> asyncpg.Pool:
    return await asyncpg.create_pool(
        settings.database_url.get_secret_value(),
        min_size=1,
        max_size=settings.db_pool_max,
        server_settings={"search_path": "skillsieve"},
        statement_cache_size=0,  # safe behind poolers
    )


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    configure_logging()
    settings = get_settings()
    pool = await open_pool(settings)
    taxonomy = await load_taxonomy(pool)
    app.state.pool = pool
    app.state.service = ExtractionService(
        cache=CacheRepository(pool),
        llm=build_llm(settings),
        budget=LlmBudget(settings.llm_rpm, settings.llm_rpd),
        taxonomy=taxonomy,
        prompt_version=settings.prompt_version,
        max_input_chars=settings.max_input_chars,
        fuzzy_min_score=settings.fuzzy_min_score,
        budget_s=settings.llm_budget_s,
    )
    log.info("startup", skills_aliases=taxonomy.size, model=settings.gemini_model)
    yield
    await pool.close()


def create_app() -> FastAPI:
    app = FastAPI(title="skillsieve skills-svc", lifespan=lifespan, docs_url=None, redoc_url=None)
    app.include_router(router)
    return app


app = create_app()
