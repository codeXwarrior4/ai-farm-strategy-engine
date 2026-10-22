# API Contract Specification

**Platform:** AI Farm Strategy & Decision Engine  
**Version:** Phase 1.1 (Stabilization & Security Hardening)  
**Base URLs:**
- **Backend (Spring Boot):** `http://localhost:8080`
- **ML Service (FastAPI):** `http://localhost:8000`
- **Frontend (React/Vite):** `http://localhost:5173`

---

## 1. Authentication & Authorization Model

### Authentication Mechanism
The platform uses stateless JSON Web Token (JWT) authentication:
- Tokens are signed using HMAC-SHA256 with a 256-bit secret loaded from environment variables (`JWT_SECRET` / `security.jwt.secret`).
- Default token validity is 24 hours (86,400,000 ms).
- Clients must include the token in the `Authorization` header with the `Bearer ` prefix on all protected routes.
- Public routes: `/api/v1/health`, `/api/v1/auth/register`, `/api/v1/auth/login`, and swagger/docs endpoints.
- Protected routes: All `/api/v1/users/{userId}/farmer-profile` endpoints.

### Authorization & Ownership Verification Rules
- The user ID in request paths (`/api/v1/users/{userId}/...`) is **never trusted** as proof of ownership.
- The `JwtAuthenticationFilter` resolves and validates the token, establishing an authenticated `UserPrincipal` in the Spring Security context.
- The service/controller verifies that the authenticated `UserPrincipal.getId()` strictly matches the `{userId}` in the URI path.
- If a client attempts to create, view, or update a profile belonging to another user ID, the request is rejected with `403 Forbidden`.
- If an unauthenticated client requests a protected resource, or if the token is invalid or expired, the request is rejected with `401 Unauthorized`.

### Request & Response Headers
| Header | Type | Description |
|---|---|---|
| `Authorization` | String | `Bearer <JWT_TOKEN>` (required for secured endpoints) |
| `Content-Type` | String | `application/json` |
| `Accept` | String | `application/json` |

### Error Response Format (RFC 7807 Compatible)
```json
{
  "timestamp": "2026-10-17T10:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied: you may only access your own profile resources",
  "path": "/api/v1/users/2/farmer-profile",
  "fieldErrors": []
}
```

---

## 2. Authentication Endpoints (Spring Boot Backend)

### Register User
#### `POST /api/v1/auth/register`
Creates a new user account with BCrypt password hashing.

**Access:** Public  
**Request Body:**
```json
{
  "username": "ramesh_farmer",
  "email": "ramesh@example.com",
  "password": "SecurePassword123!"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `username` | string | Yes | 3–50 chars, alphanumeric/underscore |
| `email` | string | Yes | Valid email format |
| `password` | string | Yes | 8–100 characters |

**Response (201 Created):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "username": "ramesh_farmer",
  "email": "ramesh@example.com",
  "roles": ["ROLE_FARMER"]
}
```

**Error Responses:**
- `400 Bad Request` — Validation failure (e.g. invalid email format or short password)
- `409 Conflict` — Username or email already registered

---

### Login User
#### `POST /api/v1/auth/login`
Authenticates user with username or email and returns a signed JWT.

**Access:** Public  
**Request Body:**
```json
{
  "usernameOrEmail": "ramesh@example.com",
  "password": "SecurePassword123!"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "username": "ramesh_farmer",
  "email": "ramesh@example.com",
  "roles": ["ROLE_FARMER"]
}
```

**Error Responses:**
- `400 Bad Request` — Missing credentials
- `401 Unauthorized` — Invalid username/email or incorrect password

---

## 3. Backend Health Endpoint

#### `GET /api/v1/health`
Checks backend operational status and database connectivity.

**Access:** Public  
**Response (200 OK):**
```json
{
  "status": "UP",
  "database": "CONNECTED",
  "timestamp": "2026-10-17T10:00:00Z",
  "version": "0.1.0",
  "service": "ai-farm-strategy-backend"
}
```

---

## 4. Farmer Profile Endpoints (Spring Boot Backend)

### Implemented Phase 1 Fields vs Planned Phase 2 Fields

The Phase 1 data model focuses strictly on core identity, parcel scale, location, and farming experience. Planned agronomic and financial modeling fields are deferred to Phase 2.

#### Phase 1 Implemented Fields
| Field | Type | Required in Create | Description |
|---|---|---|---|
| `fullName` | string | Yes | Farmer's full legal/display name (2–100 chars) |
| `phoneNumber` | string | No | Contact phone number (max 20 chars) |
| `primaryState` | string | No | Indian state / province (max 100 chars) |
| `primaryDistrict` | string | No | Primary district (max 100 chars) |
| `totalLandHectares` | BigDecimal | No | Total landholding in hectares (>= 0) |
| `yearsOfExperience` | Integer | No | Years engaged in farming (>= 0) |
| `preferredLanguage` | string | No | ISO 639-1 language code (e.g., 'en', 'hi', 'gu') |

#### Planned Phase 2 Fields (NOT Implemented in Phase 1)
| Field | Planned Type | Target Phase | Description |
|---|---|---|---|
| `cultivableHectares` | BigDecimal | Phase 2 | Net cultivable portion of landholding |
| `soilType` | Enum | Phase 2 | Soil classification (e.g. ALLUVIAL, BLACK, RED) |
| `irrigationSource` | Enum | Phase 2 | Main irrigation source (BOREWELL, CANAL, RAIN_FED) |
| `waterAvailabilityMonths` | Integer | Phase 2 | Reliable water access months per annum |
| `workingCapital` | BigDecimal | Phase 2 | Available operational budget in INR |
| `riskTolerance` | Enum | Phase 2 | Farmer risk appetite (LOW, MEDIUM, HIGH) |

---

### Create Farmer Profile
#### `POST /api/v1/users/{userId}/farmer-profile`
Creates a farmer profile linked to the specified user.

**Access:** Authenticated (Caller must be `userId`)  
**Headers:** `Authorization: Bearer <token>`  
**Request Body:**
```json
{
  "fullName": "Ramesh Patel",
  "phoneNumber": "+919876543210",
  "primaryState": "Gujarat",
  "primaryDistrict": "Kutch",
  "totalLandHectares": 5.25,
  "yearsOfExperience": 12,
  "preferredLanguage": "gu"
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "ownerId": 1,
  "ownerUsername": "ramesh_farmer",
  "ownerEmail": "ramesh@example.com",
  "fullName": "Ramesh Patel",
  "phoneNumber": "+919876543210",
  "primaryState": "Gujarat",
  "primaryDistrict": "Kutch",
  "totalLandHectares": 5.25,
  "yearsOfExperience": 12,
  "preferredLanguage": "gu",
  "createdAt": "2026-10-17T10:00:00Z",
  "updatedAt": "2026-10-17T10:00:00Z"
}
```

**Error Responses:**
- `400 Bad Request` — Validation failed (e.g. missing `fullName` or negative `yearsOfExperience`)
- `401 Unauthorized` — Missing, expired, or invalid JWT
- `403 Forbidden` — Authenticated user does not match `{userId}` in path
- `404 Not Found` — User ID does not exist in database
- `409 Conflict` — User already has a farmer profile (1:1 constraint)

---

### Get Farmer Profile
#### `GET /api/v1/users/{userId}/farmer-profile`
Retrieves the farmer profile for the specified user.

**Access:** Authenticated (Caller must be `userId`)  
**Headers:** `Authorization: Bearer <token>`  
**Response (200 OK):** Response body matches the profile representation shown above.

**Error Responses:**
- `401 Unauthorized` — Missing, expired, or invalid JWT
- `403 Forbidden` — Authenticated user does not match `{userId}` in path
- `404 Not Found` — No profile exists for the specified user

---

### Update Farmer Profile
#### `PUT /api/v1/users/{userId}/farmer-profile`
Updates the existing farmer profile for the specified user.

**Access:** Authenticated (Caller must be `userId`)  
**Headers:** `Authorization: Bearer <token>`  
**Request Body:**
```json
{
  "fullName": "Ramesh K. Patel",
  "phoneNumber": "+919876543211",
  "primaryState": "Gujarat",
  "primaryDistrict": "Kutch",
  "totalLandHectares": 6.50,
  "yearsOfExperience": 13,
  "preferredLanguage": "gu"
}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "ownerId": 1,
  "ownerUsername": "ramesh_farmer",
  "ownerEmail": "ramesh@example.com",
  "fullName": "Ramesh K. Patel",
  "phoneNumber": "+919876543211",
  "primaryState": "Gujarat",
  "primaryDistrict": "Kutch",
  "totalLandHectares": 6.50,
  "yearsOfExperience": 13,
  "preferredLanguage": "gu",
  "createdAt": "2026-10-17T10:00:00Z",
  "updatedAt": "2026-10-17T10:05:00Z"
}
```

**Behavior on Validation Failure:**
- The existing profile is preserved unmodified if validation fails.
- Returns `400 Bad Request` with field error details.

**Error Responses:**
- `400 Bad Request` — Validation failed (e.g. blank `fullName` or negative `yearsOfExperience`)
- `401 Unauthorized` — Missing, expired, or invalid JWT
- `403 Forbidden` — Authenticated user does not match `{userId}` in path
- `404 Not Found` — User or profile does not exist

---

## 5. ML Decision Service (Python FastAPI)

### Health & Scaffold
#### `GET /health`
Checks ML microservice liveness. Supports Cross-Origin Resource Sharing (CORS) for configured origins (including `http://localhost:5173` and `http://localhost:4173`).

**Access:** Public  
**Response (200 OK):**
```json
{
  "status": "UP",
  "service": "ai-farm-strategy-ml-service",
  "version": "0.1.0",
  "timestamp": "2026-10-17T10:00:00.000000"
}
```

**CORS Headers on Valid Origin:**
- `access-control-allow-origin: http://localhost:5173`
- `access-control-allow-credentials: true`

---

## 6. Planned Phase 2+ Endpoints (Roadmap / Not Implemented in Phase 1)

> [!NOTE]
> The following endpoints are architectural roadmap targets and are **NOT** implemented in Phase 1.

- `POST /api/v1/strategy/evaluate` — Evaluates candidate multi-crop portfolios against farmer land/capital constraints.
- `POST /api/v1/strategy/monte-carlo` — Runs stochastic uncertainty stress tests across historical weather and price variations.
- `GET /api/v1/market/prices` — Real-time mandi prices and net effective return comparison.
