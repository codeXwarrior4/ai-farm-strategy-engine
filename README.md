# AI Farm Strategy & Decision Engine

> **Tagline:** "From What Should I Grow? to Where & When Should I Sell?"

A platform helping farmers evaluate land, soil, water, working capital, weather and market conditions; compare feasible farming strategies; understand economic outcomes; stress-test uncertainty; and compare markets by estimated net effective return.

---

## Repository Structure

```
ai-farm-strategy-engine/
├── frontend/          React + TypeScript + Tailwind CSS
├── backend/           Java 21 + Spring Boot + MySQL
├── ml-service/        Python + FastAPI
├── database/          Database setup instructions
└── docs/              API contract and architecture docs
```

## Quick Start

### Prerequisites

| Requirement | Version |
|---|---|
| Java | 21 (LTS) |
| Node.js | 18+ |
| npm | 9+ |
| Python | 3.10+ |
| MySQL | 8.x |

> Maven is bundled as the Maven Wrapper (`mvnw`) inside `backend/` — no separate Maven install required.

---

### 1. Database Setup

See [`database/README.md`](database/README.md) for the one-time MySQL setup commands.

---

### 2. Backend (Spring Boot)

```bash
cd backend

# Copy and fill environment variables
copy .env.example .env

# Run with Maven Wrapper (Windows)
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

# Or on Linux/Mac
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Health check: `GET http://localhost:8080/api/v1/health`

---

### 3. ML Service (FastAPI)

```bash
cd ml-service

# Create and activate virtual environment
python -m venv .venv
.venv\Scripts\activate        # Windows
# source .venv/bin/activate   # Linux/Mac

pip install -r requirements.txt

uvicorn app.main:app --reload --port 8000
```

Health check: `GET http://localhost:8000/health`

---

### 4. Frontend (React)

```bash
cd frontend

npm install

# Copy and fill environment variables
copy .env.example .env.local

npm run dev
```

App: `http://localhost:5173`

---

## Environment Variables

Each service has a `.env.example` file. **Never commit `.env` files with real secrets.**

| Service | File |
|---|---|
| Backend | `backend/.env.example` |
| ML Service | `ml-service/.env.example` |
| Frontend | `frontend/.env.example` |

---

## API Documentation

See [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md).

---

## Development Status

This repository is at **Phase 1** — foundational infrastructure only.

No AI predictions, crop recommendations or market data are implemented.

---

## Disclaimer

This system provides decision-support information only. It does **not** guarantee yield, profit or prediction accuracy. The farmer remains the final decision-maker.
