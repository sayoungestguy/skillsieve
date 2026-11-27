"""skills-svc: internal API that turns job post text into canonical skills."""

import asyncio
import contextlib
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
from app.messaging.handler import JobCreatedHandler
from app.messaging.jobs import JobTextRepository
from app.messaging.worker import ExtractionWorker, run_until_cancelled
from app.metrics import LLM_DAILY_LIMIT, http_metrics
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


async def refresh_taxonomy(service: ExtractionService, pool: asyncpg.Pool, every_s: float) -> None:
    """Keep aliases current: core-api owns the seed and may migrate after this service starts."""
    while True:
        await asyncio.sleep(every_s)
        try:
            fresh = await load_taxonomy(pool)
        except Exception as e:  # keep serving with the old taxonomy
            log.warning("taxonomy.refresh_failed", error=type(e).__name__)
            continue
        if fresh.size != service.taxonomy.size or fresh.aliases != service.taxonomy.aliases:
            log.info("taxonomy.refreshed", before=service.taxonomy.size, after=fresh.size)
            service.taxonomy = fresh


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
    LLM_DAILY_LIMIT.set(settings.llm_rpd)
    log.info("startup", skills_aliases=taxonomy.size, model=settings.gemini_model)
    refresher = asyncio.create_task(
        refresh_taxonomy(app.state.service, pool, settings.taxonomy_refresh_s)
    )
    tasks = [refresher]
    if settings.amqp_url is not None and settings.amqp_url.get_secret_value():
        handler = JobCreatedHandler(app.state.service, JobTextRepository(pool))
        worker = ExtractionWorker(settings.amqp_url.get_secret_value(), handler)
        tasks.append(asyncio.create_task(run_until_cancelled(worker)))
    else:
        log.info("worker.disabled", reason="AMQP_URL not set")
    yield
    for task in tasks:
        task.cancel()
    for task in tasks:
        with contextlib.suppress(asyncio.CancelledError):
            await task
    await pool.close()


def create_app() -> FastAPI:
    app = FastAPI(title="skillsieve skills-svc", lifespan=lifespan, docs_url=None, redoc_url=None)
    app.include_router(router)
    app.middleware("http")(http_metrics)
    return app


app = create_app()
