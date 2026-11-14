# Real Government AGMARKNET / data.gov.in Mandi Price Integration Report

**AI Farm Strategy & Decision Engine**  
**Version:** 0.1.0-SNAPSHOT  
**Date:** November 14, 2026  
**Status:** Verification & Architecture Complete — Ready for Live API Ingestion

---

## 1. Executive Summary

This document details the architectural completion and data-integrity hardening of the mandi market price integration in the **AI Farm Strategy & Decision Engine**.

Previously, the application relied on demonstration sample records (seeded in Flyway migration `V9` for Jalna district, Maharashtra). The audit revealed:
1. Seeded records were displayed without an explicit indicator distinguishing sample data from live AGMARKNET observations.
2. The UI labeled wholesale market records under the district name rather than identifying specific APMC market yards (e.g. Jalna Mandi, Ambad Mandi, Partur Mandi).
3. The deduplication key did not incorporate data provenance, creating a potential collision where sample records could prevent ingestion of future official government observations.
4. Strategy engine financial recommendations did not strictly isolate unverified demonstration prices from production-grade financial assertions.

We have now implemented **end-to-end data provenance**, **secure API configuration**, **strict domain validation**, **deterministic query prioritization**, **strategy engine safeguards**, **transparent UI labeling**, and **comprehensive PDF advisory reporting**.

---

## 2. Architecture & Data Flow

```
                                    +------------------------------+
                                    | data.gov.in / AGMARKNET API   |
                                    | Resource ID:                 |
                                    | 9ef84268-d588...             |
                                    +--------------+---------------+
                                                   |
                                         (HTTPS GET with API Key)
                                                   v
+----------------------------------------------------------------------------------+
| Backend Data Ingestion (Spring Boot 3.4.1)                                       |
|                                                                                  |
|   1. DataGovInMarketService                                                      |
|      - Bounded pagination (maxPages, pageSize)                                   |
|      - Rate-limit handling (HTTP 429) & Server error handling (HTTP 500)         |
|      - Zero credential leaks in logs/diagnostics                                 |
|                                                                                  |
|   2. MarketPriceIngestionService                                                 |
|      - Strict Price Validation (minPrice <= maxPrice, non-negative)              |
|      - Arrival Date Validation (ISO / DD/MM/YYYY, rejects future dates)          |
|      - Provenance Tagging: OFFICIAL_API vs DEMO_SEEDED                           |
|      - 64-char SHA-256 Deduplication Hash (includes provenance)                  |
|                                                                                  |
|   3. MySQL 8 / H2 Persistence (Flyway V5, V9, V10)                               |
|      - market_price_records table with source_classification                     |
|                                                                                  |
|   4. MarketPriceServiceImpl                                                      |
|      - Priority Querying: OFFICIAL_API -> DEMO_SEEDED -> NO_DATA                 |
|      - Freshness Policy: <= 7 days = FRESH, > 7 days = STALE                     |
|      - Discloses liveSyncStatus and dominantSourceClassification                 |
+--------------------------+-----------------------------------+-------------------+
                           |                                   |
                           v                                   v
+------------------------------------+   +-----------------------------------------+
| Strategy Decision Engine           |   | Frontend (React 19 + TypeScript)        |
|                                    |   |                                         |
| - Rejects high confidence on DEMO  |   | - APMC Mandi Yard Labeling              |
| - Assigns DEMO_DATA status         |   | - Provenance Badges (Official vs Demo)  |
| - Emits DEMO_MARKET_DATA warning   |   | - Live Sync Status Indicator            |
| - Downgrades to CONDITIONAL tier   |   | - Dynamic PDF Advisory Dossier Export   |
+------------------------------------+   +-----------------------------------------+
```

---

## 3. Data Provenance & Schema Migration

### Source Classification (`MarketPriceSourceClassification`)
Market records are classified under three explicit categories:
- `OFFICIAL_API`: Direct, authenticated observation ingested from the official data.gov.in AGMARKNET feed.
- `DEMO_SEEDED`: Seeded demonstration records (such as `V9`) used for offline testing, UI design, and development environments.
- `MANUAL_VERIFIED`: Authorized manual government gazette or verified extension records.

### Flyway Migration `V10`
- `V10__add_market_price_source_classification.sql` was added to `backend/src/main/resources/db/migration`, `dev-migration`, and `test-migration`.
- Adds `source_classification VARCHAR(30) NOT NULL DEFAULT 'DEMO_SEEDED'`.
- Enforces check constraint: `CHECK (source_classification IN ('OFFICIAL_API', 'DEMO_SEEDED', 'MANUAL_VERIFIED'))`.
- Automatically migrates existing historical records to `DEMO_SEEDED`.
- Adds index `idx_mpr_provenance (state, district, commodity, source_classification, arrival_date)`.

### Deterministic SHA-256 Deduplication Hash
To prevent sample demo records from colliding with future live records, `calculateDeduplicationKey` incorporates the source classification into the 64-character hash:
$$\text{SHA-256}(\text{state} \mid \text{district} \mid \text{market} \mid \text{commodity} \mid \text{variety} \mid \text{grade} \mid \text{arrivalDate} \mid \text{sourceClassification})$$

---

## 4. Freshness Policy & Ingestion Safeguards

### Ingestion Validation
- **Arrival Date**: Rejects dates in the future relative to `LocalDate.now()`. Missing dates are rejected; they are never synthesized or substituted with today's date.
- **Price Invariants**: Validates $\text{minPrice} \le \text{maxPrice}$ and non-negativity. Modal price is preserved as null when unrecorded by the APMC auction yard, avoiding artificial zeros.
- **Error Handling**: Converts HTTP 429 into `MarketRateLimitException` and network failures into `MarketTimeoutException` or `MarketProviderException`.

### Freshness Status
- `FRESH`: Official observations recorded within the last 7 days.
- `STALE`: Official observations older than 7 days relative to evaluation date.
- `DEMO_DATA`: Records present but classified as `DEMO_SEEDED`.
- `NO_DATA`: No market observations stored for the requested state/district/commodity.
- `NO_LOCATION`: Farmer profile lacks primary state or district.

---

## 5. Decision Engine & Financial Recommendation Safeguards

Phase 8 requirements enforce that demonstration prices cannot support production-grade financial assertions:
1. `MarketAttractivenessStatus` includes `DEMO_DATA`.
2. When market data evaluates to `DEMO_DATA`:
   - `FarmStrategyServiceImpl` assigns `MarketAttractivenessStatus.DEMO_DATA`.
   - Injects warning: `"DEMO_MARKET_DATA: Market prices originate from demonstration sample data and cannot support production financial assertions."`
   - Forces `hasOperationalCaveats() == true`, restricting candidates from unconditional `RECOMMENDED` status and placing them into the `CONDITIONAL` decision tier.
   - Discloses demo data caveats in the decision explanation.

---

## 6. Frontend & PDF Transparency

### UI Enhancements (`FarmerMarketPricesSection.tsx`)
- **Explicit Mandi Yard Display**: Distinguishes the market yard name (e.g. `APMC Mandi: Jalna`, `APMC Mandi: Ambad`) from the general administrative district.
- **Provenance Badges**:
  - `OFFICIAL AGMARKNET DATA` (emerald badge)
  - `DEMO DATA` (purple badge)
  - `STALE DATA` (amber badge)
  - `NO RECENT DATA` (slate badge)
  - `LIVE SYNC UNAVAILABLE` (displayed when API key is unconfigured)
- **Informative Banners**: Stale and demo warning banners explain data limitations without blocking application exploration.

### PDF Advisory Dossier (`farmReportPdfGenerator.ts`)
- **Warning Callout**: Displays an explicit `DEMONSTRATION MARKET DATA NOTICE` banner whenever market records originate from seeded sample data.
- **Table Columns**: Reflects `Commodity`, `Variety & Grade`, `APMC Mandi (Yard)`, `Arrival Date`, `Min Price`, `Modal Price`, `Max Price`, and `Source` (Official vs Demo).

---

## 7. How to Configure & Ingest Live Government Data

To begin ingesting live AGMARKNET observations:

1. **Obtain API Key**:
   - Register on [data.gov.in](https://data.gov.in).
   - Navigate to **My Account -> API Key** to obtain your personal API key.

2. **Configure Environment Variable**:
   - Create a `.env` file in the project root:
     ```bash
     DATA_GOV_IN_API_KEY=your_actual_data_gov_in_api_key_here
     ```
   - Or set in PowerShell:
     ```powershell
     $env:DATA_GOV_IN_API_KEY = "your_actual_data_gov_in_api_key_here"
     ```

3. **Trigger Administrative Ingestion**:
   - Ingest all commodities for Jalna district, Maharashtra:
     ```bash
     curl -X POST http://localhost:8080/api/v1/admin/market-prices/ingest \
       -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
       -H "Content-Type: application/json" \
       -d '{
         "filters": {
           "state": "Maharashtra",
           "district": "Jalna"
         },
         "maxPages": 5
       }'
     ```

---

## 8. Automated Test Verification Summary

All automated backend and frontend test suites pass with zero failures:

| Test Suite | Tests Run | Failures | Status |
| :--- | :---: | :---: | :---: |
| `DataGovInMarketServiceTest` | 10 | 0 | **PASS** |
| `MarketPriceIngestionServiceTest` | 13 | 0 | **PASS** |
| `MarketPriceServiceImplTest` | 10 | 0 | **PASS** |
| `AdminMarketPriceControllerTest` | 8 | 0 | **PASS** |
| `FarmStrategyServiceTest` | 23 | 0 | **PASS** |
| **Total Backend Tests** | **64** | **0** | **100% PASS** |
| **Frontend Production Build (`tsc -b && vite build`)** | **292 modules** | **0** | **SUCCESS** |

