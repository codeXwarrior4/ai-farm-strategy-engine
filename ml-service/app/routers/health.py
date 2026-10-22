"""Health router for the ML service."""

from datetime import datetime, timezone
from fastapi import APIRouter
from app.schemas.health import HealthResponse

router = APIRouter(tags=["Health"])


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="ML service health check",
    description=(
        "Returns the operational status of the ML service. "
        "A 200 response with status='UP' confirms the service is running."
    ),
)
def health_check() -> HealthResponse:
    return HealthResponse(
        status="UP",
        service="ai-farm-strategy-ml-service",
        version="0.1.0",
        timestamp=datetime.now(timezone.utc).isoformat(),
    )
