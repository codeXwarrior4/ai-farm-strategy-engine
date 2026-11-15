# Maharashtra-Wide AGMARKNET Mandi Market Price Coverage

**AI Farm Strategy & Decision Engine**
**Resource ID:** `9ef84268-d588-465a-a308-a864a43d0070` (data.gov.in / AGMARKNET)
**Status:** Implemented & Verified Across All 36 Districts of Maharashtra

---

## 1. Executive Summary

This document describes the Maharashtra-wide market price architecture in the **AI Farm Strategy & Decision Engine**.

Previously, the application was constrained to Jalna district demonstration sample records seeded via Flyway migration `V9`. The engine has now been extended to provide **full 36-district coverage across Maharashtra**, supporting live official data retrieval from data.gov.in AGMARKNET Resource `9ef84268-d588-465a-a308-a864a43d0070`, robust district normalization and alias mapping, APMC mandi yard and commodity discovery, bounded pagination, district-level fault isolation, and transparent provenance reporting.

---

## 2. Authoritative 36-District Maharashtra Catalog

Maharashtra comprises **36 revenue districts** organized into **6 administrative divisions**. The catalog is authoritative and declared in `MaharashtraDistricts.java` and accessible via public API.

| # | Canonical District | Administrative Division | Primary data.gov.in API Name | Recognized Aliases |
|---|---|---|---|---|
| 1 | **Ahmednagar** | Nashik | `Ahilyanagar` | `Ahilyanagar`, `Ahmednagar`, `Ahmadnagar` |
| 2 | **Akola** | Amravati | `Akola` | `Akola` |
| 3 | **Amravati** | Amravati | `Amravati` | `Amravati`, `Amraoti` |
| 4 | **Beed** | Chhatrapati Sambhajinagar | `Beed` | `Beed`, `Bid` |
| 5 | **Bhandara** | Nagpur | `Bhandara` | `Bhandara` |
| 6 | **Buldhana** | Amravati | `Buldhana` | `Buldhana`, `Buldana` |
| 7 | **Chandrapur** | Nagpur | `Chandrapur` | `Chandrapur`, `Chanda` |
| 8 | **Chhatrapati Sambhajinagar** | Chhatrapati Sambhajinagar | `Chattrapati Sambhajinagar` | `Chattrapati Sambhajinagar`, `Chhatrapati Sambhajinagar`, `Aurangabad` |
| 9 | **Dhule** | Nashik | `Dhule` | `Dhule`, `Dhulia` |
| 10 | **Gadchiroli** | Nagpur | `Gadchiroli` | `Gadchiroli` |
| 11 | **Gondia** | Nagpur | `Gondia` | `Gondia`, `Gondiya` |
| 12 | **Hingoli** | Chhatrapati Sambhajinagar | `Hingoli` | `Hingoli` |
| 13 | **Jalgaon** | Nashik | `Jalgaon` | `Jalgaon` |
| 14 | **Jalna** | Chhatrapati Sambhajinagar | `Jalna` | `Jalna` |
| 15 | **Kolhapur** | Pune | `Kolhapur` | `Kolhapur` |
| 16 | **Latur** | Chhatrapati Sambhajinagar | `Latur` | `Latur` |
| 17 | **Mumbai City** | Konkan | `Mumbai` | `Mumbai City`, `Mumbai`, `Bombay` |
| 18 | **Mumbai Suburban** | Konkan | `Mumbai Suburban` | `Mumbai Suburban`, `Suburban Mumbai` |
| 19 | **Nagpur** | Nagpur | `Nagpur` | `Nagpur` |
| 20 | **Nanded** | Chhatrapati Sambhajinagar | `Nanded` | `Nanded` |
| 21 | **Nandurbar** | Nashik | `Nandurbar` | `Nandurbar` |
| 22 | **Nashik** | Nashik | `Nashik` | `Nashik`, `Nasik` |
| 23 | **Dharashiv** | Chhatrapati Sambhajinagar | `Dharashiv` | `Dharashiv`, `Osmanabad` |
| 24 | **Palghar** | Konkan | `Palghar` | `Palghar` |
| 25 | **Parbhani** | Chhatrapati Sambhajinagar | `Parbhani` | `Parbhani` |
| 26 | **Pune** | Pune | `Pune` | `Pune`, `Poona` |
| 27 | **Raigad** | Konkan | `Raigad` | `Raigad`, `Raigarh` |
| 28 | **Ratnagiri** | Konkan | `Ratnagiri` | `Ratnagiri` |
| 29 | **Sangli** | Pune | `Sangli` | `Sangli` |
| 30 | **Satara** | Pune | `Satara` | `Satara` |
| 31 | **Sindhudurg** | Konkan | `Sindhudurg` | `Sindhudurg` |
| 32 | **Solapur** | Pune | `Solapur` | `Solapur`, `Sholapur` |
| 33 | **Thane** | Konkan | `Thane` | `Thane`, `Thana` |
| 34 | **Wardha** | Nagpur | `Wardha` | `Wardha` |
| 35 | **Washim** | Amravati | `Washim` | `Washim` |
| 36 | **Yavatmal** | Amravati | `Yavatmal` | `Yavatmal`, `Yeotmal` |

### Official Gazette Renaming Harmonization
The official data.gov.in resource uses newly gazetted district names for recent observations. Our adapter resolves queries bidirectionally:
- **Ahilyanagar**: Official gazetted name for Ahmednagar. Ingestion queries `filters[district]=Ahilyanagar` and `filters[district]=Ahmednagar`.
- **Chattrapati Sambhajinagar**: Official gazetted name for Aurangabad. Ingestion queries both `filters[district]=Chattrapati Sambhajinagar` and `filters[district]=Aurangabad`.
- **Dharashiv**: Official gazetted name for Osmanabad. Ingestion queries both `filters[district]=Dharashiv` and `filters[district]=Osmanabad`.

---

## 3. API Endpoints

### 1. Maharashtra District Catalog (Public)
- **Endpoint:** `GET /api/v1/market-prices/districts/maharashtra`
- **Security:** Public (`permitAll`)
- **Response:** Array of `MaharashtraDistrictDto` objects containing `canonicalName`, `primaryApiName`, `division`, and `aliases`.

### 2. Farmer Mandi Market Prices
- **Endpoint:** `GET /api/v1/users/{userId}/farmer-profile/market-prices`
- **Security:** Authenticated (`ROLE_FARMER` or `ROLE_ADMIN`, principal ID match)
- **Query Parameters:**
  - `market` (optional): Filter by specific APMC market yard (e.g. `Pune APMC`, `Kolhapur`, `Lasalgaon`).
  - `commodity` (optional): Filter by commodity name (e.g. `Onion`, `Soyabean`).
  - `arrivalDate` (optional): Exact arrival date (`YYYY-MM-DD`).
  - `page` (optional, default: `0`): Zero-based page number.
  - `pageSize` (optional, default: `20`): Page size (1 to 50).
  - `limit` (optional): Legacy alias for `pageSize`.
- **Response Highlights:**
  - `availableMarkets`: List of distinct APMC mandis available in the farmer's district.
  - `availableCommodities`: List of distinct commodities present in that district.
  - `totalPages`, `totalElements`, `page`, `pageSize`: Standard Spring Data pagination metadata.
  - `freshness`: Reports `sourceClassification` (`OFFICIAL_API` vs `DEMO_SEEDED`), days old, and freshness status.

### 3. Single-District Admin Ingestion
- **Endpoint:** `POST /api/v1/admin/market-prices/ingest/district`
- **Security:** Authenticated (`ROLE_ADMIN`)
- **Payload:**
  ```json
  {
    "state": "Maharashtra",
    "district": "Kolhapur",
    "arrivalDate": "2026-11-15",
    "maxPages": 5
  }
  ```
- **Response:** `DistrictIngestionSummaryDto` detailing `totalFetched`, `savedCount`, `skippedCount`, `errorCount`, and status.

### 4. Statewide Maharashtra Ingestion (Fault-Isolated)
- **Endpoint:** `POST /api/v1/admin/market-prices/ingest/maharashtra`
- **Security:** Authenticated (`ROLE_ADMIN`)
- **Payload:**
  ```json
  {
    "arrivalDate": null,
    "maxPagesPerDistrict": 3
  }
  ```
- **Response:** `StatewideIngestionReport` categorizing all 36 districts into:
  - `successfulDistricts`: Districts with successfully ingested arrival records.
  - `emptyDistricts`: Districts reporting 0 records on the queried date.
  - `failedDistricts`: Districts where network/provider errors occurred (fault-isolated; other districts continue unaffected).

---

## 4. How to Test & Ingest Market Data

### Ingesting Single District via cURL
```bash
# Ingest live official records for Kolhapur
curl -X POST "http://localhost:8080/api/v1/admin/market-prices/ingest/district" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"state":"Maharashtra","district":"Kolhapur","maxPages":5}'
```

### Ingesting All 36 Districts Statewide
```bash
# Ingest live official records for all 36 districts of Maharashtra
curl -X POST "http://localhost:8080/api/v1/admin/market-prices/ingest/maharashtra" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"maxPagesPerDistrict":3}'
```

### Automated Maven Test Suites
```powershell
# 1. Run Comprehensive Maharashtra 36-District Coverage Tests (12 tests)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"; cd backend; .\mvnw.cmd test "-Dtest=MaharashtraMarketPriceCoverageTest"

# 2. Run API Client & Admin Ingestion Tests
cd backend; .\mvnw.cmd test "-Dtest=DataGovInMarketServiceTest,AdminMarketPriceControllerTest"

# 3. Run Service Layer & Controller Regression Tests
cd backend; .\mvnw.cmd test "-Dtest=MarketPriceServiceImplTest,FarmerProfileMarketPricesControllerTest,MarketPriceIngestionServiceTest"

# 4. Run Strategy Engine & Economic Validation Tests
cd backend; .\mvnw.cmd test "-Dtest=FarmStrategyServiceTest,FarmStrategyEconomicIntegrationTest,CropEconomicEvaluationServiceTest,OfficialDataQualityAndEconomicValidationTest"
```

---

## 5. AGMARKNET API Realities & Operational Safeguards

### 1. Trading Days & Market Closures
- AGMARKNET records wholesale arrivals from active physical APMC auction yards.
- On Sundays, national holidays, or local mandi strike/closure days, many mandis do not hold open auctions.
- On Sunday, November 15, 2026, 11 Maharashtra districts reported active arrivals (257 records total: Ahilyanagar, Buldhana, Chattrapati Sambhajinagar, Gadchiroli, Jalgaon, Kolhapur, Nagpur, Pune, Raigad, Satara, Solapur), while 25 districts had 0 auctions recorded.
- **Rule:** When a district reports zero arrivals for a date, the application reports this honestly (`emptyDistricts`, empty list in UI with message: *"No official AGMARKNET records are currently available for this district or selected date."*). It **never invents prices** or substitutes another district's data.

### 2. Mandatory Zero-Substitution Policy
- Under no circumstance will the system replace a missing district price with another district's rates (e.g. Kolhapur will never show Pune or Jalna data).
- Strategy financial calculations evaluate missing market data as `NO_MARKET_DATA` and retain the crop under the `DATA_DEFICIENT` tier with explicit advisories.

### 3. Separation of Official & Demo Records
- Ingestion enforces source classifications: `OFFICIAL_API` vs `DEMO_SEEDED`.
- The deterministic SHA-256 natural key includes provenance:
  $$\text{SHA-256}(\text{state} \mid \text{district} \mid \text{market} \mid \text{commodity} \mid \text{variety} \mid \text{grade} \mid \text{arrivalDate} \mid \text{sourceClassification})$$
- `OFFICIAL_API` records are prioritized over `DEMO_SEEDED` records in queries.
- Seeded demo records are never converted or disguised as official government observations.

### 4. Rate-Limiting & Bounded Pagination
- data.gov.in enforces API rate limits and page ceilings.
- `DataGovInMarketService` and `MarketPriceIngestionService` implement configurable bounded pagination (`maxPages`, `pageSize`), exponential backoff, and handle HTTP 429 (`MarketRateLimitException`) gracefully.
