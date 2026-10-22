"""Tests for the ML service health endpoint."""

import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_health_returns_200():
    """GET /health should return HTTP 200."""
    response = client.get("/health")
    assert response.status_code == 200


def test_health_response_schema():
    """GET /health should return expected JSON fields."""
    response = client.get("/health")
    data = response.json()

    assert data["status"] == "UP"
    assert data["service"] == "ai-farm-strategy-ml-service"
    assert data["version"] == "0.1.0"
    assert "timestamp" in data
    assert data["timestamp"]  # non-empty


def test_health_content_type_is_json():
    """GET /health should return application/json content type."""
    response = client.get("/health")
    assert "application/json" in response.headers["content-type"]


def test_cors_allowed_origin():
    """Requests with allowed origin should receive Access-Control-Allow-Origin."""
    response = client.get("/health", headers={"Origin": "http://localhost:5173"})
    assert response.status_code == 200
    assert response.headers.get("access-control-allow-origin") == "http://localhost:5173"
    assert response.headers.get("access-control-allow-credentials") == "true"


def test_cors_disallowed_origin():
    """Requests with disallowed origin should NOT receive Access-Control-Allow-Origin."""
    response = client.get("/health", headers={"Origin": "http://evil-site.com"})
    assert response.status_code == 200
    assert "access-control-allow-origin" not in response.headers


def test_cors_preflight_options():
    """Preflight OPTIONS request should return appropriate CORS headers."""
    response = client.options(
        "/health",
        headers={
            "Origin": "http://localhost:5173",
            "Access-Control-Request-Method": "GET",
            "Access-Control-Request-Headers": "authorization",
        },
    )
    assert response.status_code == 200
    assert response.headers.get("access-control-allow-origin") == "http://localhost:5173"
    assert "GET" in response.headers.get("access-control-allow-methods", "")
    assert response.headers.get("access-control-allow-credentials") == "true"

