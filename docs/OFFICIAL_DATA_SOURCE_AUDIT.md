# Phase 5A: Official Agricultural Data-Source Audit

**Project:** AI Farm Strategy & Decision Engine  
**Author:** AI Strategy Engine Engineering Team  
**Date:** October 2026  
**Status:** COMPLETE & VERIFIED

---

## 1. Executive Summary

This audit establishes the definitive technical and methodological specification for all external agricultural and economic datasets ingested by the AI Farm Strategy & Decision Engine.

The engine requires four distinct benchmark and observation datasets:
1. **Empirical Crop Yield Benchmarks** (Production capacity)
2. **Cost of Cultivation Benchmarks — Cost A2** (Working capital and financial feasibility)
3. **Statutory Minimum Support Price (MSP) Floors** (Regulatory price reference)
4. **Wholesale Mandi Auction Prices** (Market attractiveness and scenario pricing)

This document formalizes the official publishing agencies, access mechanisms, schemas, canonical entity mappings, unit transformations, freshness policies, and provenance requirements to ensure zero synthetic data enters production.

---

## 2. Dataset Specifications

### 2.1 Empirical Crop Yield Benchmarks

| Dimension | Specification |
| :--- | :--- |
| **Official Publisher** | Directorate of Economics and Statistics (DES), Department of Agriculture & Farmers Welfare (DA&FW), Ministry of Agriculture and Farmers Welfare (MoA&FW), Government of India. |
| **Primary Publications** | 1. *Agricultural Statistics at a Glance* (Annual Series, e.g., 2022, 2023)<br>2. *Area and Production of Principal Crops in India* (DES)<br>3. National Open Data Portal: `data.gov.in` / `desagri.gov.in` / `eands.dacnet.nic.in` |
| **Verified URL** | `https://desagri.gov.in/` and `https://eands.dacnet.nic.in/` |
| **Access Requirements** | Public open access under Government Open Data License - India (GODL). Machine-readable downloads available in CSV/PDF/XLSX. |
| **Scope & Granularity** | State-level annual averages and Triennium Ending (TE) averages by cropping season (`KHARIF`, `RABI`, `ZAID`). National baseline available as `ALL_INDIA`. |
| **Source Unit** | Kilograms per Hectare ($\text{kg/ha}$) in DES primary tables. |
| **Application Unit** | **Quintals per Hectare** (`QUINTAL_PER_HA`).<br>$\text{yield}_{\text{q/ha}} = \frac{\text{yield}_{\text{kg/ha}}}{100}$. Stored as `DECIMAL(10, 2)`. |
| **Update Frequency** | Annually (published with 1–2 year reporting and reconciliation lag). |
| **Staleness Threshold** | Records older than 5 years relative to planning year generate an advisory warning (`HISTORICAL_YIELD_BENCHMARK`). |
| **Fallback Hierarchy** | State + Season $\to$ State + `ALL_SEASONS` $\to$ `ALL_INDIA` + Season (triggers `NATIONAL_YIELD_FALLBACK`) $\to$ `ALL_INDIA` + `ALL_SEASONS` $\to$ `DATA_DEFICIENT`. |
| **Persistence Target** | Table: `crop_yield_benchmarks` (Flyway V8). |

---

### 2.2 Cost of Cultivation Benchmarks (Cost A2)

| Dimension | Specification |
| :--- | :--- |
| **Official Publisher** | Commission for Agricultural Costs and Prices (CACP) & DES, MoA&FW, Government of India. |
| **Primary Scheme** | *Comprehensive Scheme for Studying the Cost of Cultivation of Principal Crops in India*. |
| **Primary Publications** | 1. CACP *Price Policy for Kharif Crops* (Annual CACP Report)<br>2. CACP *Price Policy for Rabi Crops* (Annual CACP Report)<br>3. DES *Cost of Cultivation of Principal Crops in India* |
| **Verified URL** | `https://cacp.dacnet.nic.in/` |
| **Definition of Cost A2** | **Actual paid-out operational costs incurred in cash and kind by the farmer:**<br>• Seeds (purchased / farm-saved)<br>• Fertilizers, bio-fertilizers, and manures<br>• Plant protection chemicals (pesticides, insecticides, weedicides)<br>• Hired human labor (casual and attached)<br>• Hired animal / bullock labor<br>• Machine / tractor labor (hired and owned fuel/maintenance)<br>• Irrigation fees and fuel/electricity charges for tube-wells<br>• Land revenue, cess, and local agricultural taxes<br>• Depreciation on farm implements, tools, and farm buildings<br>• Interest on working capital. |
| **Excluded from Cost A2** | ⚠️ **DO NOT CONFUSE COST A2 WITH COMPREHENSIVE COST (C2):**<br>• **Cost A2+FL** adds the imputed value of family labor.<br>• **Cost C2** adds imputed rent of owned land and interest on fixed capital.<br>The strategy engine sizes working capital based strictly on **Cost A2** (out-of-pocket operating cash). |
| **Application Unit** | **INR per Hectare** (`INR_PER_HA`). Stored as `DECIMAL(12, 2)`. |
| **Update Frequency** | Triennial survey cycles updated annually via price indexing by CACP. |
| **Persistence Target** | Table: `crop_cost_benchmarks` (Flyway V7). |

---

### 2.3 Statutory Minimum Support Price (MSP) Floors

| Dimension | Specification |
| :--- | :--- |
| **Official Publisher** | Cabinet Committee on Economic Affairs (CCEA) & Department of Agriculture and Farmers Welfare (DA&FW), Government of India, upon CACP recommendations. |
| **Primary Publications** | 1. CACP *Price Policy Reports*<br>2. CCEA Press Releases via Press Information Bureau (PIB)<br>3. Gazette of India Notifications (Department of Food and Public Distribution) |
| **Verified URL** | `https://pib.gov.in/` and `https://cacp.dacnet.nic.in/` |
| **Scope & Coverage** | 22 mandated agricultural crops: 14 Kharif, 6 Rabi, and 2 Commercial crops (plus Fair and Remunerative Price for Sugarcane). |
| **Application Unit** | **INR per Quintal** (`INR_PER_QUINTAL`). Stored as `DECIMAL(12, 2)`. |
| **Reference Period** | Crop marketing year / Marketing season (e.g., `"Kharif Marketing Season (KMS) 2024-25"`, `"Rabi Marketing Season (RMS) 2025-26"`). |
| **Regulatory Caveat** | ⚠️ **Statutory MSP is an informational procurement reference floor, NOT a guaranteed open-market transaction realization:**<br>Private wholesale mandis may clear at, above, or below statutory MSP depending on spot supply/demand, moisture content, FAQ specifications, and local APMC auction dynamics. |
| **Persistence Target** | Table: `crop_msp_floors` (Flyway V8). |

---

### 2.4 Wholesale Mandi Auction Prices

| Dimension | Specification |
| :--- | :--- |
| **Official Publisher** | Directorate of Marketing & Inspection (DMI), MoA&FW, Government of India. |
| **Source Portal** | National Open Government Data Portal (`data.gov.in`) under National Data Sharing and Accessibility Policy (NDSAP). |
| **API Endpoint** | `https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070` |
| **Resource Title** | *Current Daily Price and Arrival of Agricultural Commodities from APMC Mandis*. |
| **Authentication** | API Key passed via `api-key` query parameter (`DATA_GOV_IN_API_KEY`). |
| **Rate Limits** | 10,000 queries per month under standard NDSAP developer tier. |
| **Observed Fields** | `state`, `district`, `market`, `commodity`, `variety`, `arrival_date`, `min_price`, `max_price`, `modal_price`. |
| **Application Unit** | **INR per Quintal** (`INR_PER_QUINTAL`). Stored as `DECIMAL(10, 2)`. |
| **Freshness Policy** | Mandatory 7-day freshness window (`MARKET_FRESHNESS_THRESHOLD_DAYS = 7`). Prices older than 7 days relative to evaluation date are stamped `isStale = true` (`STALE_MARKET_DATA`). |
| **Persistence Target** | Table: `market_price_records` (Flyway V5). |

---

## 3. Crop Mapping & Taxonomic Alignment

The catalog maps 8 canonical crops across botanical names, AGMARKNET market commodities, and CACP survey classifications:

| Canonical Crop ID | Canonical Name | Botanical Name | Category | Primary Season | AGMARKNET Mandi Commodity | CACP Survey Label |
| :---: | :--- | :--- | :--- | :---: | :--- | :--- |
| **1** | **Wheat** | *Triticum aestivum* | `CEREAL` | `RABI` | `Wheat` | `Wheat` |
| **2** | **Paddy** | *Oryza sativa* | `CEREAL` | `KHARIF` | `Paddy(Dhan)(Common)` | `Paddy` |
| **3** | **Cotton** | *Gossypium hirsutum* | `COMMERCIAL` | `KHARIF` | `Cotton` | `Cotton` |
| **4** | **Soybean** | *Glycine max* | `OILSEED` | `KHARIF` | `Soyabean` | `Soyabean` |
| **5** | **Chickpea** | *Cicer arietinum* | `PULSE` | `RABI` | `Bengal Gram(Gram)(Whole)` | `Gram` |
| **6** | **Mustard** | *Brassica juncea* | `OILSEED` | `RABI` | `Mustard` | `Rapeseed & Mustard` |
| **7** | **Groundnut** | *Arachis hypogaea* | `OILSEED` | `KHARIF` | `Groundnut` | `Groundnut` |
| **8** | **Maize** | *Zea mays* | `CEREAL` | `KHARIF` | `Maize` | `Maize` |

---

## 4. Geographic Alignment & Normalization

All state and UT references are normalized via `StateNormalizer` to canonical uppercase strings (e.g., `"GUJARAT"`, `"MADHYA PRADESH"`, `"PUNJAB"`, `"HARYANA"`, `"RAJASTHAN"`, `"MAHARASHTRA"`).

National aggregations and fallbacks use the standardized token: `'ALL_INDIA'`.

---

## 5. Provenance & Audit Integrity

Every imported benchmark record MUST contain:
1. `source_organization`: Official issuing agency (`DES-MoA&FW`, `CACP-MoA&FW`, `DMI-AGMARKNET`).
2. `source_publication`: Publication title and year.
3. `source_table_ref` / `source_notification_ref`: Exact table number, Annexure, or Gazette reference.
4. `reference_period`: Explicit agricultural crop year (e.g., `"2022-23"`, `"KMS 2024-25"`).
5. `evidence_tier`: Must be `VERIFIED_DIRECT` or `OFFICIAL_STATUTORY`.
6. `revision_version`: Monotonically increasing revision integer.

---

## 6. Phase 5A Exit Gate Verification

- [x] Official publishers, publications, and URLs documented.
- [x] Scope, definition, and exclusions of Cost A2 verified against CACP methodology.
- [x] Unit conversions ($\text{kg/ha} \to \text{qtl/ha}$) specified and checked.
- [x] Statutory MSP reference rules confirmed as non-guaranteed procurement benchmarks.
- [x] AGMARKNET / data.gov.in API parameters, resource IDs, and rate limits audited.
- [x] 8 canonical crops mapped across agronomic, market, and survey taxonomies.
