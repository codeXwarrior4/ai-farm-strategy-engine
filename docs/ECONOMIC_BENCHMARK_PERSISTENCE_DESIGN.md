# Phase 4B: Economic Data Model & Official Benchmark Schemas — Design Specification

## 1. Overview & Objectives
Phase 4B establishes the persistence layer for empirical **crop-yield benchmarks** and **Minimum Support Price (MSP)** regulatory floors for the AI Farm Strategy & Decision Engine.

Following the principles established in Phase 3 for Cost A2 benchmarks (`crop_cost_benchmarks`), this phase introduces:
1. Two dedicated database tables: `crop_yield_benchmarks` and `crop_msp_floors`.
2. Clean JPA entities and Spring Data repositories.
3. Deterministic lookup providers with explicit fallback hierarchy and zero synthetic extrapolation.
4. Strict absence handling (`Optional.empty()`), ensuring missing records never produce fabricated yield or price guarantees.

---

## 2. Domain Entities & Schema Specifications

### 2.1 Table: `crop_yield_benchmarks`

#### Purpose & Units
Stores empirical crop yield observations sourced from official government publications (Directorate of Economics & Statistics, Ministry of Agriculture & Farmers Welfare, or ICAR institutes).
- **Yield Unit:** Standardized strictly as **Quintals per Hectare** (`yield_unit = 'QUINTAL_PER_HA'`).
- **Precision:** `DECIMAL(10, 2)` (strictly positive, `> 0`).

#### Schema Definition (MySQL & H2 Compatible)
| Column Name | Type | Nullable | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `NO` | Auto Inc | Primary Key. |
| `crop_id` | `BIGINT` | `NO` | — | Foreign key to `crop_master(id)` with `ON DELETE RESTRICT`. |
| `state` | `VARCHAR(100)` | `NO` | — | Canonical uppercase Indian state or UT (or `'ALL_INDIA'`). |
| `season` | `VARCHAR(30)` | `NO` | — | Cropping season: `KHARIF`, `RABI`, `ZAID`, `ALL_SEASONS`, `WHOLE_YEAR`, `PERENNIAL`. |
| `benchmark_year` | `INT` | `NO` | — | Survey / commencement year (e.g. 2022 for 2022-23 crop year). |
| `reference_period` | `VARCHAR(50)` | `NO` | — | Exact reference period (e.g. `"2021-22 to 2023-24 Triennium"`, `"2022-23"`). |
| `yield_quintals_per_hectare` | `DECIMAL(10, 2)` | `NO` | — | Empirical observed average yield. Must be `> 0`. |
| `yield_unit` | `VARCHAR(30)` | `NO` | `'QUINTAL_PER_HA'` | Measurement unit. |
| `crop_source_label` | `VARCHAR(150)` | `YES` | `NULL` | Source crop name as printed in the official report. |
| `evidence_tier` | `VARCHAR(30)` | `NO` | `'VERIFIED_DIRECT'` | Evidence qualification tier (`VERIFIED_DIRECT`, `UNVERIFIED`). |
| `data_nature` | `VARCHAR(30)` | `NO` | `'OFFICIAL_STATISTICS'` | Nature of observation (`OFFICIAL_STATISTICS`, `OBSERVED_SURVEY`, `TRIENNIUM_AVERAGE`). |
| `source_organization` | `VARCHAR(150)` | `NO` | — | Issuing body (e.g. `"DES-MoA&FW"`, `"ICAR"`). |
| `source_publication` | `VARCHAR(255)` | `NO` | — | Official publication title (e.g. `"Agricultural Statistics at a Glance 2023"`). |
| `source_url` | `VARCHAR(500)` | `YES` | `NULL` | Direct URL to publication / repository record. |
| `source_table_ref` | `VARCHAR(100)` | `YES` | `NULL` | Table or page number citation in source document. |
| `revision_version` | `INT` | `NO` | `1` | Monotonically increasing revision counter. |
| `is_active` | `BOOLEAN` | `NO` | `TRUE` | Soft active indicator. |
| `active_flag` | `TINYINT` | `YES` | `1` | Token for unique constraint (`1` for active, `NULL` for superseded). |
| `superseded_by_id` | `BIGINT` | `YES` | `NULL` | Self-referencing FK to successor record. |
| `notes` | `TEXT` / `VARCHAR(1000)` | `YES` | `NULL` | Methodological notes or survey caveats. |
| `created_at` | `DATETIME` | `NO` | `CURRENT_TIMESTAMP` | Audit creation timestamp. |
| `updated_at` | `DATETIME` | `NO` | `CURRENT_TIMESTAMP` | Audit update timestamp. |

#### Constraints & Indexes
- **Natural Identity:** `UNIQUE (crop_id, state, season, benchmark_year, source_organization, revision_version)`
- **Single Active Record:** `UNIQUE (crop_id, state, season, benchmark_year, source_organization, active_flag)`
- **Check Constraints:**
  - `yield_quintals_per_hectare > 0`
  - `yield_unit IN ('QUINTAL_PER_HA')`
  - `season IN ('KHARIF', 'RABI', 'ZAID', 'ALL_SEASONS', 'WHOLE_YEAR', 'PERENNIAL')`
  - `(is_active = TRUE AND active_flag = 1) OR (is_active = FALSE AND active_flag IS NULL)`
- **Index:** `idx_cyb_lookup ON (crop_id, state, season, is_active)`

---

### 2.2 Table: `crop_msp_floors`

#### Purpose & Units
Stores statutory Minimum Support Price (MSP) rates declared by the Government of India on the recommendations of the Commission for Agricultural Costs and Prices (CACP).
- **Price Unit:** Standardized strictly as **INR per Quintal** (`price_unit = 'INR_PER_QUINTAL'`, `currency = 'INR'`).
- **Precision:** `DECIMAL(12, 2)` (strictly positive, `> 0`).

#### Schema Definition (MySQL & H2 Compatible)
| Column Name | Type | Nullable | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `NO` | Auto Inc | Primary Key. |
| `crop_id` | `BIGINT` | `NO` | — | Foreign key to `crop_master(id)` with `ON DELETE RESTRICT`. |
| `season` | `VARCHAR(30)` | `NO` | — | Marketing season category: `KHARIF`, `RABI`, `ZAID`, `ALL_SEASONS`, `WHOLE_YEAR`. |
| `crop_year` | `INT` | `NO` | — | Marketing season commencement year (e.g. 2024 for 2024-25 season). |
| `marketing_season_label` | `VARCHAR(50)` | `NO` | — | Official season label (e.g. `"KMS 2024-25"`, `"RMS 2025-26"`). |
| `state_scope` | `VARCHAR(100)` | `NO` | `'ALL_INDIA'` | Geographical scope (`'ALL_INDIA'` for national MSP, or state name for SAP/bonus). |
| `variety_or_grade` | `VARCHAR(100)` | `NO` | `'COMMON'` | Variety/grade distinction (e.g. `"COMMON"`, `"GRADE_A"`, `"MEDIUM_STAPLE"`). |
| `msp_inr_per_quintal` | `DECIMAL(12, 2)` | `NO` | — | Statutory MSP floor price. Must be `> 0`. |
| `price_unit` | `VARCHAR(30)` | `NO` | `'INR_PER_QUINTAL'` | Unit of measure. |
| `currency` | `VARCHAR(10)` | `NO` | `'INR'` | Currency code. |
| `evidence_tier` | `VARCHAR(30)` | `NO` | `'OFFICIAL_STATUTORY'` | Evidence tier (`OFFICIAL_STATUTORY`). |
| `source_organization` | `VARCHAR(150)` | `NO` | `'CACP-MoA&FW'` | Declaring agency (default CACP / Cabinet Committee on Economic Affairs). |
| `source_publication` | `VARCHAR(255)` | `NO` | — | Official notification / price policy title. |
| `source_url` | `VARCHAR(500)` | `YES` | `NULL` | Press release or gazette notification link. |
| `source_notification_ref` | `VARCHAR(100)` | `YES` | `NULL` | Official notification / CCEA resolution reference. |
| `revision_version` | `INT` | `NO` | `1` | Monotonically increasing revision counter. |
| `is_active` | `BOOLEAN` | `NO` | `TRUE` | Soft active indicator. |
| `active_flag` | `TINYINT` | `YES` | `1` | Token for unique constraint (`1` for active, `NULL` for superseded). |
| `superseded_by_id` | `BIGINT` | `YES` | `NULL` | Self-referencing FK to successor record. |
| `notes` | `TEXT` / `VARCHAR(1000)` | `YES` | `NULL` | Clarifications on procurement bonuses or quality specs. |
| `created_at` | `DATETIME` | `NO` | `CURRENT_TIMESTAMP` | Audit creation timestamp. |
| `updated_at` | `DATETIME` | `NO` | `CURRENT_TIMESTAMP` | Audit update timestamp. |

#### Constraints & Indexes
- **Natural Identity:** `UNIQUE (crop_id, state_scope, season, crop_year, variety_or_grade, revision_version)`
- **Single Active Record:** `UNIQUE (crop_id, state_scope, season, crop_year, variety_or_grade, active_flag)`
- **Check Constraints:**
  - `msp_inr_per_quintal > 0`
  - `price_unit IN ('INR_PER_QUINTAL')`
  - `currency IN ('INR')`
  - `season IN ('KHARIF', 'RABI', 'ZAID', 'ALL_SEASONS', 'WHOLE_YEAR')`
  - `(is_active = TRUE AND active_flag = 1) OR (is_active = FALSE AND active_flag IS NULL)`
- **Index:** `idx_cmf_lookup ON (crop_id, state_scope, season, crop_year, is_active)`

---

## 3. Lookup and Fallback Hierarchy

### 3.1 Crop Yield Benchmark Provider (`CropYieldBenchmarkProvider`)
Resolves expected yield ($\text{qtl/ha}$) deterministically:
1. **Exact Match:** `(cropId, canonicalState, season, activeFlag=1)` ordered by `benchmarkYear DESC, revisionVersion DESC, id DESC`.
2. **Season-Agnostic Fallback:** `(cropId, canonicalState, 'ALL_SEASONS', activeFlag=1)` ordered by `benchmarkYear DESC, revisionVersion DESC`.
3. **National Match:** `(cropId, 'ALL_INDIA', season, activeFlag=1)` ordered by `benchmarkYear DESC, revisionVersion DESC`.
4. **National All-Seasons Match:** `(cropId, 'ALL_INDIA', 'ALL_SEASONS', activeFlag=1)`.
5. **No Match:** Returns `Optional.empty()` (never fabricates or guesses yield).

### 3.2 Crop MSP Floor Provider (`CropMspFloorProvider`)
Resolves regulatory price floor ($\text{₹/qtl}$) deterministically:
1. **State & Season Match:** `(cropId, canonicalState, season, cropYear, activeFlag=1)` (for state-specific SAP/bonus).
2. **National MSP Match:** `(cropId, 'ALL_INDIA', season, cropYear, activeFlag=1)`.
3. **Latest Year Fallback:** If `cropYear` is omitted or unseeded, latest active: `(cropId, 'ALL_INDIA', season, activeFlag=1)` ordered by `cropYear DESC`.
4. **All-Seasons Fallback:** `(cropId, 'ALL_INDIA', 'ALL_SEASONS', activeFlag=1)` ordered by `cropYear DESC`.
5. **No Match:** Returns `Optional.empty()` (for non-MSP crops, e.g. fruits/vegetables).

---

## 4. H2 and MySQL Compatibility Notes

| Feature | MySQL 8.x (`db/migration`) | H2 In-Memory (`db/dev-migration`, `db/test-migration`) |
| :--- | :--- | :--- |
| Table creation | `CREATE TABLE table_name` with `ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci` | `CREATE TABLE IF NOT EXISTS table_name` |
| Text column type | `notes TEXT NULL` | `notes VARCHAR(1000) NULL` |
| Timestamps | `updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | `updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| Secondary indexes | Defined inline or via `CREATE INDEX` | Defined via `CREATE INDEX IF NOT EXISTS` after table creation |
| Null uniqueness | Single-active token (`active_flag = 1` vs `NULL`) is compatible with both engines because standard SQL treats NULLs as non-equal in unique indexes. | Fully compatible with H2 `MODE=MySQL`. |

---

## 5. Missing Data Behavior & Seed Policy
- **Zero Synthetic Seed Records:** Tables are created empty. No guessed, approximate, or synthetic numbers are seeded into migrations V8.
- **Strict Absence Propagation:** When benchmarks are not present, repositories and providers return `Optional.empty()`.
- **Phase 3 Preservation:** The existing strategy engine, financial feasibility evaluator, and 5-tier classification continue to operate without modification.
