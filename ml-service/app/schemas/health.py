"""Pydantic schemas for the health endpoint."""

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    """Response schema for GET /health."""

    status: str = Field(..., examples=["UP"], description="Service operational status")
    service: str = Field(..., examples=["ai-farm-strategy-ml-service"])
    version: str = Field(..., examples=["0.1.0"])
    timestamp: str = Field(..., description="ISO 8601 UTC timestamp")

    model_config = {"json_schema_extra": {"example": {
        "status": "UP",
        "service": "ai-farm-strategy-ml-service",
        "version": "0.1.0",
        "timestamp": "2025-01-01T05:30:00+00:00",
    }}}
