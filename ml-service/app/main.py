"""
AI Farm Strategy & Decision Engine — ML Service
Phase 1: Health scaffold only.

No models are trained or loaded in this phase.
No predictions are returned.
Future crop suitability, yield estimation and market trend models
will be added in later phases after data assessment.
"""

import os
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import health

cors_origins_env = os.getenv(
    "CORS_ORIGINS",
    "http://localhost:5173,http://localhost:4173,http://127.0.0.1:5173,http://127.0.0.1:4173",
)
allowed_origins = [origin.strip() for origin in cors_origins_env.split(",") if origin.strip()]

app = FastAPI(
    title="AI Farm Strategy ML Service",
    description=(
        "Machine learning service for the AI Farm Strategy & Decision Engine. "
        "Phase 1 provides the service scaffold and health endpoint only. "
        "No predictions are returned in this phase."
    ),
    version="0.1.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)

