import hmac
from typing import Annotated

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status

from app.config import Settings, get_settings
from app.extraction.models import ExtractionResult, ExtractRequest
from app.extraction.service import ExtractionService
from app.llm.errors import LlmNotConfiguredError, LlmQuotaError, LlmUnavailableError

router = APIRouter()


def require_internal_token(
    settings: Annotated[Settings, Depends(get_settings)],
    x_internal_token: Annotated[str | None, Header()] = None,
) -> None:
    expected = settings.internal_token.get_secret_value()
    if x_internal_token is None or not hmac.compare_digest(x_internal_token, expected):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "missing or wrong X-Internal-Token")


def get_service(request: Request) -> ExtractionService:
    service: ExtractionService | None = getattr(request.app.state, "service", None)
    if service is None:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "not ready")
    return service


@router.get("/healthz")
async def healthz() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/readyz")
async def readyz(request: Request) -> dict[str, object]:
    service = get_service(request)
    try:
        await request.app.state.pool.fetchval("SELECT 1")
    except Exception as e:  # any DB error means not ready
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "database unreachable") from e
    return {"status": "ok", "skills": service.taxonomy.size}


@router.post(
    "/v1/extract",
    response_model=ExtractionResult,
    response_model_by_alias=True,
    dependencies=[Depends(require_internal_token)],
)
async def extract(
    body: ExtractRequest, service: Annotated[ExtractionService, Depends(get_service)]
) -> ExtractionResult:
    try:
        return await service.extract(body.text_hash, body.text)
    except LlmQuotaError as e:
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, str(e)) from e
    except (LlmNotConfiguredError, LlmUnavailableError) as e:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, str(e)) from e
