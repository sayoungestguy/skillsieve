"""Prometheus metrics (TECHNICAL_DESIGN §11). Exposed at GET /metrics on the internal port."""

import time
from collections.abc import Awaitable, Callable

from prometheus_client import Counter, Gauge, Histogram
from starlette.requests import Request
from starlette.responses import Response
from starlette.routing import Match

LLM_CALLS = Counter(
    "skillsieve_llm_calls", "Gemini calls by outcome", ["result"]
)  # result: ok | error | quota
CACHE_HITS = Counter("skillsieve_llm_cache_hits", "Extractions answered from extraction_cache")
EXTRACTION_SECONDS = Histogram(
    "skillsieve_extraction_duration_seconds",
    "Time to extract skills from one post (cache hit or LLM call)",
    ["cached"],
    buckets=(0.05, 0.1, 0.25, 0.5, 1, 2, 3, 5, 8, 10, 15),
)
LLM_USED_TODAY = Gauge("skillsieve_llm_calls_today", "LLM calls used in the current quota day")
LLM_DAILY_LIMIT = Gauge("skillsieve_llm_daily_limit", "Our LLM calls-per-day budget (LLM_RPD)")
TAILOR_CALLS = Counter(
    "skillsieve_tailor_calls", "Local-model resume tailoring calls by outcome", ["result"]
)  # result: ok | error
TAILOR_SECONDS = Histogram(
    "skillsieve_tailor_duration_seconds",
    "Time for one tailoring answer from the local model",
    buckets=(1, 2, 5, 10, 20, 30, 45, 60, 90, 120),
)
HTTP_SECONDS = Histogram(
    "skillsieve_http_request_duration_seconds",
    "skills-svc HTTP latency",
    ["method", "route", "status"],
)


def _route(request: Request) -> str:
    """Route template (/v1/extract), never the raw path, to keep label cardinality small."""
    route = request.scope.get("route")  # set by the router once it matched
    path = getattr(route, "path", None)
    if isinstance(path, str):
        return path
    for candidate in request.app.routes:
        match, _ = candidate.matches(request.scope)
        if match == Match.FULL:
            return str(getattr(candidate, "path", "other"))
    return "other"


async def http_metrics(
    request: Request, call_next: Callable[[Request], Awaitable[Response]]
) -> Response:
    if request.url.path == "/metrics":
        return await call_next(request)
    start = time.perf_counter()
    status = 500
    try:
        response = await call_next(request)
        status = response.status_code
        return response
    finally:
        HTTP_SECONDS.labels(request.method, _route(request), str(status)).observe(
            time.perf_counter() - start
        )
