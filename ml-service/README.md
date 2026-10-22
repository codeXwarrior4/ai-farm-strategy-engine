# AI Farm Strategy — ML Service

## Overview

Minimal FastAPI service providing the ML infrastructure scaffold for Phase 1.

**Phase 1 status:** Health endpoint only. No models are trained or loaded.
Future crop suitability, yield estimation and market trend models will be added in later phases.

---

## Requirements

- Python 3.10+
- pip

---

## Setup and Run

```bash
# 1. Create virtual environment
python -m venv .venv

# 2. Activate it
.venv\Scripts\activate        # Windows PowerShell
# source .venv/bin/activate   # Linux / macOS

# 3. Install dependencies
pip install -r requirements.txt

# 4. Copy example env (optional in Phase 1)
copy .env.example .env

# 5. Start the server
uvicorn app.main:app --reload --port 8000
```

Service is available at: **http://localhost:8000**

Interactive API docs: **http://localhost:8000/docs**

---

## Health Check

```
GET http://localhost:8000/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "ai-farm-strategy-ml-service",
  "version": "0.1.0",
  "timestamp": "2025-01-01T05:30:00+00:00"
}
```

---

## Running Tests

```bash
# Activate virtual environment first, then:
pytest tests/ -v
```

Expected output: 3 tests passing.

---

## Project Structure

```
ml-service/
├── app/
│   ├── main.py          # FastAPI app creation and router registration
│   ├── routers/
│   │   └── health.py    # GET /health endpoint
│   └── schemas/
│       └── health.py    # Pydantic response model
├── tests/
│   └── test_health.py   # pytest health tests
├── requirements.txt
├── .env.example
└── README.md
```

---

## Disclaimer

This service provides decision-support infrastructure only.
No AI predictions, crop recommendations or market data are returned in Phase 1.
