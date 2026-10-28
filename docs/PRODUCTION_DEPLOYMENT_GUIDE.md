# Production Deployment & Operations Guide
**Project:** AI Farm Strategy & Decision Engine  
**Target Environment:** Production Linux / Containerized (Docker, Kubernetes)  
**Database Target:** MySQL 8.0+  
**JDK Runtime:** Java 21 LTS  
**Frontend Runtime:** Node.js 20+ LTS / Nginx Static Bundle  

---

## 1. System Architecture & Component Topology

```
                       [ HTTPS / TLS 1.3 ]
                                |
                    +-----------v-----------+
                    |   Reverse Proxy/CDN   |
                    |   (Nginx / Cloudflare)|
                    +-----+-----------+-----+
                          |           |
             Static Assets|           |/api/v1/*
                          v           v
            +---------------+   +-----------------------------+
            | Vite / React  |   | Spring Boot 3.4.1 (Java 21) |
            | Frontend Dist |   | Port 8080                   |
            +---------------+   +--------------+--------------+
                                               |
                                     HikariCP  | JDBC
                                               v
                                +-----------------------------+
                                |      MySQL 8.0+ Database    |
                                | (Flyway Migrations V1 -> V8)|
                                +-----------------------------+
```

---

## 2. Database Specification & Migration Lifecycle

### 2.1 MySQL 8.0 Requirements
- **Storage Engine:** `InnoDB`
- **Character Set:** `utf8mb4`
- **Collation:** `utf8mb4_unicode_ci`
- **Supported Feature Set:** Standard SQL CHECK constraints, partial/conditional unique indexing via active flag tokens, foreign keys with explicit cascade controls (`ON DELETE RESTRICT`, `ON DELETE SET NULL`).

### 2.2 Flyway Migration Lineage (V1 – V8)
| Version | Description | Target Tables | Integrity / Constraints |
|---|---|---|---|
| **V1** | Initial user identity schema | `users` | Username/email uniqueness, BCrypt hashed passwords, role assignment. |
| **V2** | Farmer profile core schema | `farmer_profiles`, `soil_types`, `water_sources` | Farmer parcel attributes, coordinates, constraints. |
| **V3** | Agricultural master catalog | `crop_master`, `crop_season_requirements`, `crop_soil_compatibilities`, `crop_water_requirements` | Foreign keys with ON DELETE RESTRICT, canonical crop catalog. |
| **V4** | Mandi market price cache | `market_price_records` | SHA-256 deduplication key, arrival date index, price range bounds. |
| **V5** | Weather cache and daily forecast | `weather_forecast_records` | Spatial grid lookup, timestamp indexing, forecast bounds. |
| **V6** | Core catalog crop seed data | `crop_master` (8 catalog crops) | 8 verified baseline crops (Wheat, Paddy, Cotton, Soybean, Chickpea, Mustard, Groundnut, Maize). |
| **V7** | Official Cost A2 benchmark schema | `crop_cost_benchmarks` | Positive Cost A2 check constraint, natural identity unique constraint, single-active unique constraint with `active_flag`. |
| **V8** | Yield benchmarks & statutory MSP | `crop_yield_benchmarks`, `crop_msp_floors` | Positive yield (q/ha) and MSP (INR/q) check constraints, single-active unique indexes, foreign key linkages. |

### 2.3 HikariCP Connection Pool Configuration (Production)
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.max-lifetime=1200000
spring.datasource.hikari.leak-detection-threshold=60000
```

### 2.4 Backup & Disaster Recovery Runbook
- **Automated Nightly Backup:**
  ```bash
  mysqldump --single-transaction --quick --lock-tables=false \
    -u "${DB_USER}" -p"${DB_PASSWORD}" -h "${DB_HOST}" \
    --default-character-set=utf8mb4 "${DB_NAME}" | gzip > "backup_${DB_NAME}_$(date +%Y%m%d_%H%M%S).sql.gz"
  ```
- **Point-in-Time Restoration:**
  ```bash
  gunzip < backup_file.sql.gz | mysql -u "${DB_USER}" -p"${DB_PASSWORD}" -h "${DB_HOST}" "${DB_NAME}"
  ```

---

## 3. Security Architecture & Threat Mitigation

### 3.1 Authentication & Token Lifecycle
- **Algorithm:** HMAC-SHA256 (HS256) with minimum 256-bit entropy signing secret.
- **Session Policy:** `SessionCreationPolicy.STATELESS`.
- **Token Validity:** 24 hours standard expiry; rejected immediately if tampered, expired, or malformed.
- **Header:** `Authorization: Bearer <JWT_TOKEN>`.

### 3.2 Authorization Matrix
| Route Group | Required Role | Failure Behavior |
|---|---|---|
| `GET /api/v1/health` | Public / Anonymous | 200 OK |
| `/api/v1/auth/**` | Public / Anonymous | 400 Bad Request / 401 Unauthorized |
| `/api/v1/users/{userId}/farmer-profile/**` | `ROLE_FARMER` or `ROLE_ADMIN` (Principal must match `{userId}`) | 401 Unauthorized / 403 Forbidden |
| `/api/v1/users/{userId}/farmer-profile/strategy` | `ROLE_FARMER` or `ROLE_ADMIN` (Principal must match `{userId}`) | 401 Unauthorized / 403 Forbidden |
| `/api/v1/admin/**` | `ROLE_ADMIN` strictly enforced | 401 Unauthorized / 403 Forbidden |

### 3.3 Data Ownership Enforcement
All farmer-facing endpoints enforce ownership through `FarmerProfileAccessValidator` and Spring Security context:
- Requests from authenticated user $A$ attempting to read, update, or run strategies for user $B$ are rejected with `403 Access Denied`.

### 3.4 Production CORS Security
- `app.cors.allowed-origins` must be restricted to explicit production domains (e.g., `https://farmengine.example.com`). Wildcards (`*`) are disallowed when `allowCredentials=true`.

---

## 4. Operational Telemetry, Health, and Ingestion Diagnostics

### 4.1 Health Check Endpoints
- **Service Liveness:** `GET /api/v1/health`
  - Response: `{"status":"UP","service":"ai-farm-strategy-backend","timestamp":"..."}`
- **Database Readiness:** Validated via Spring DataSource connection ping.

### 4.2 Official Benchmark Ingestion Endpoints (Admin Only)
- `POST /api/v1/admin/benchmarks/yields/ingest`
- `POST /api/v1/admin/benchmarks/costs/ingest`
- `POST /api/v1/admin/benchmarks/msp/ingest`
- `POST /api/v1/admin/benchmarks/batch/ingest`
- `POST /api/v1/admin/benchmarks/seed/ingest`
- `GET /api/v1/admin/benchmarks/coverage`

### 4.3 Ingestion Diagnostics & Idempotency
- **Deduplication:** Automatic matching against active benchmarks prevents duplicate records from inflating the database.
- **Revision Handling:** Ingesting updated figures for an existing `(crop, state, season, year, sourceOrg)` tuple deactivates the previous active record (`active_flag = NULL`, `superseded_by_id = newRecord.id`) and inserts the new revision with incremented `revisionVersion`.
- **Actionable Rejections:** Batch ingestion does not fail atomically on one bad record. Bad records are collected in `rejectionReasons` with row index and validation explanation.

### 4.4 External Provider Resilience & Circuit Breaking
| Provider | Endpoint Role | Timeout Bounds | Failure Behavior |
|---|---|---|---|
| **data.gov.in / AGMARKNET** | Mandi wholesale arrivals | Connect: 5000ms, Read: 10000ms | Returns cached local prices; warns of staleness (>7 days) without blocking strategies. |
| **Open-Meteo** | Weather forecast | Connect: 5000ms, Read: 5000ms | Returns cached forecasts; falls back gracefully if network unavailable. |

---

## 5. Environment Configuration (Variable Names Only)

> [!CAUTION]
> Never commit actual credentials or private keys to source control. Set the following environment variables in your production container runtime or secrets vault.

### Backend Required Variables
```bash
# Database Connectivity
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF8
SPRING_DATASOURCE_USERNAME=${DB_USER}
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}

# Security & Tokens
APP_JWT_SECRET=${JWT_SIGNING_KEY_MIN_32_CHARS}
APP_JWT_EXPIRATION_MS=86400000

# CORS & Gateway
APP_CORS_ALLOWED_ORIGINS=https://${PRODUCTION_FRONTEND_DOMAIN}

# External API Integrations (Optional / Provider Specific)
DATA_GOV_IN_API_KEY=${DATA_GOV_IN_KEY}
```

### Frontend Required Variables
```bash
VITE_API_BASE_URL=https://${PRODUCTION_API_DOMAIN}
```

---

## 6. Build, Deployment, and Rollback Runbooks

### 6.1 Backend Production Build & Execution
```bash
cd backend
./mvnw clean package -DskipTests
java -server -Xms1024m -Xmx2048m \
     -XX:+UseG1GC -XX:+ExplicitGCInvokesConcurrent \
     -jar target/ai-farm-strategy-backend-0.1.0-SNAPSHOT.jar
```

### 6.2 Frontend Production Build & Deployment
```bash
cd frontend
npm ci
npm run build
# Deploy 'dist/' directory to Nginx, AWS S3 / CloudFront, or container runtime
```

### 6.3 Deployment Verification Checklist
1. Health endpoint returns 200 UP: `curl -f https://${API_DOMAIN}/api/v1/health`
2. Flyway migrations applied successfully: verify schema version is at least V8.
3. Authenticated admin executes seed benchmark ingestion:
   ```bash
   curl -X POST https://${API_DOMAIN}/api/v1/admin/benchmarks/seed/ingest \
     -H "Authorization: Bearer ${ADMIN_JWT_TOKEN}"
   ```
4. Verify coverage report shows 8 catalog crops fully populated:
   ```bash
   curl -X GET https://${API_DOMAIN}/api/v1/admin/benchmarks/coverage \
     -H "Authorization: Bearer ${ADMIN_JWT_TOKEN}"
   ```

### 6.4 Rollback Procedure
1. **Container / Application Rollback:** Re-deploy previous known-good container image tag.
2. **Database Rollback:** If a forward schema migration fails, restore MySQL from the pre-deployment snapshot using the restoration runbook in Section 2.4. Do NOT attempt manual DROP commands against production tables.

---

## 7. Verification Status Matrix

| Subsystem / Capability | Verification Level | Verification Evidence |
|---|---|---|
| Flyway Migrations (V1–V8) | **VERIFIED (LOCAL H2/MYSQL MODE)** | 345/345 backend integration tests passing with Flyway schema migration. |
| Production MySQL 8.0 Live Instance | **NOT VERIFIED (HOST ENVIRONMENT)** | Requires live credentials and provisioned external MySQL cluster. |
| JWT Authentication & Access Control | **VERIFIED** | 100% verified via automated integration and security mock tests. |
| Admin Benchmark Ingestion Endpoints | **VERIFIED** | Verified through `AdminBenchmarkControllerTest` and `BenchmarkIngestionServiceTest`. |
| Official Benchmark Catalog Seeding | **VERIFIED** | Verified with 8 catalog crops, 70+ records, 100% coverage report. |
| Economic Evaluation Engine Arithmetic | **VERIFIED** | Verified through `OfficialDataQualityAndEconomicValidationTest` and `CropEconomicEvaluationServiceTest`. |
| Frontend Production Compilation & Lint | **VERIFIED** | `tsc -b && vite build` built in 6.87s; oxlint 0 errors. |
| Live External Government Mandi API | **NOT VERIFIED (LIVE EXTERNAL)** | data.gov.in requires live registered API key and outbound government gateway availability. |
