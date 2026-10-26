# Strategy Engine API Contract Specification

**Platform:** AI Farm Strategy & Decision Engine  
**Document:** Strategy Engine REST API Contract  
**Phase:** 3A (Design & Specification)  
**Status:** DRAFT / PENDING APPROVAL  

---

## 1. Authentication & Security Architecture

The Strategy Engine REST API adheres strictly to the security, authorization, and error formatting standards established in [API_CONTRACT.md](API_CONTRACT.md):

### Authentication & Tenant Isolation
- **Authentication:** Stateless JSON Web Token (JWT) Bearer authentication via standard `Authorization: Bearer <token>` header.
- **Tenant Isolation:** The URI path contains the farmer's `{userId}`. The `validateOwnership(principal, userId)` security guard strictly validates that:
  $$\text{principal.getId()} == \{\text{userId}\}$$
- **Cross-Tenant Prevention:** If an authenticated user attempts to run a strategy evaluation for a different user's profile ID, the request is rejected with `403 Forbidden`.
- **Unauthenticated Access:** Requests missing or presenting expired/invalid tokens are rejected with `401 Unauthorized`.

### Common Headers
| Header | Value | Description |
|---|---|---|
| `Authorization` | `Bearer <JWT_TOKEN>` | Required on all protected strategy routes |
| `Content-Type` | `application/json` | Required for `POST` bodies |
| `Accept` | `application/json` | Response representation |

### Standard RFC 7807 Error Response
```json
{
  "timestamp": "2026-10-25T15:30:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "You are not authorized to access or modify this farmer profile",
  "path": "/api/v1/users/2/farmer-profile/strategy/evaluate",
  "fieldErrors": []
}
```

---

## 2. API Endpoints Specification

---

### 2.1. Evaluate Farm Strategy

#### `POST /api/v1/users/{userId}/farmer-profile/strategy/evaluate`
Executes a multi-dimensional strategy evaluation for the authenticated farmer, assessing crop suitability, financial feasibility, and market price trends for a target season.

**Access:** Authenticated (`ROLE_FARMER` or `ROLE_ADMIN`), strictly enforcing user ownership (`principal.id == {userId}`).  
**Idempotency:** Evaluation is stateless and side-effect free; repeated calls with identical parameters return identical results.

#### Request Body Schema
```json
{
  "targetSeason": "KHARIF",
  "planningYear": 2026,
  "cultivableHectaresOverride": 2.5000,
  "workingCapitalOverride": 120000.00,
  "candidateCropIds": [1, 2, 5, 8]
}
```

#### Request Fields
| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `targetSeason` | string | **Yes** | Enum: `KHARIF`, `RABI`, `ZAID` | Target agricultural season for crop planning. |
| `planningYear` | integer | No | $2024 \le \text{year} \le 2035$ | Defaults to current calendar year if omitted. |
| `cultivableHectaresOverride` | BigDecimal | No | $> 0.0000$ and $\le 10000.0000$ | Optional speculative override to test different plot sizes without editing the profile. |
| `workingCapitalOverride` | BigDecimal | No | $\ge 0.00$ | Optional speculative budget override to test different capital scenarios. |
| `candidateCropIds` | array of Long | No | Max 50 items | Optional list of specific `CropMaster` IDs to evaluate. If omitted or empty, all active crops suitable for `targetSeason` are evaluated. |

---

#### Success Response (`200 OK`)
```json
{
  "evaluationId": "strat-20261025-00129",
  "farmerProfileId": 1,
  "ownerId": 1,
  "targetSeason": "KHARIF",
  "planningYear": 2026,
  "evaluationTimestamp": "2026-10-25T15:30:00Z",
  "evaluatedContext": {
    "primaryState": "Gujarat",
    "primaryDistrict": "Kutch",
    "soilType": "BLACK",
    "irrigationSource": "BOREWELL",
    "waterAvailabilityMonths": 8,
    "cultivableHectares": 2.5000,
    "workingCapitalInr": 120000.00,
    "riskTolerance": "MEDIUM"
  },
  "summary": {
    "totalCandidatesEvaluated": 4,
    "suitableAndFeasibleCount": 2,
    "conditionallyFeasibleCount": 1,
    "excludedCount": 1
  },
  "suitableAndFeasibleCrops": [
    {
      "cropId": 5,
      "cropName": "Soybean",
      "botanicalName": "Glycine max",
      "category": "OILSEED",
      "overallStatus": "SUITABLE_AND_FEASIBLE",
      "agronomicSuitability": {
        "status": "HIGHLY_SUITABLE",
        "soilFit": {
          "status": "HIGH",
          "soilType": "BLACK",
          "notes": "Black soil possesses high moisture retention optimal for soybean vegetative growth."
        },
        "waterFit": {
          "status": "HIGH",
          "requiredMonths": 4,
          "availableMonths": 8,
          "waterRequirementMm": "450 - 650 mm",
          "notes": "Available water duration exceeds crop cycle by 4 months."
        },
        "seasonFit": {
          "status": "HIGH",
          "targetSeason": "KHARIF",
          "sowingWindow": "June - July"
        },
        "climateFit": {
          "status": "HIGH",
          "temperatureTolerance": "Within optimal range (22°C - 32°C)",
          "heatStressRisk": false
        },
        "factorExplanations": [
          "Soil, season, and water duration fully satisfy agronomic requirements."
        ]
      },
      "financialFeasibility": {
        "status": "FEASIBLE",
        "costPerHectareA2": 32000.00,
        "requiredWorkingCapitalFullParcel": 80000.00,
        "availableWorkingCapital": 120000.00,
        "capitalSufficiencyRatio": 1.50,
        "affordableHectares": 2.5000,
        "isCapitalConstrained": false,
        "riskAlignment": "ALIGNED",
        "explanation": "Available working capital (₹1,20,000) fully covers cultivation costs (₹80,000) for the entire 2.5 ha plot with a 33% liquidity buffer."
      },
      "marketAttractiveness": {
        "status": "FAVORABLE",
        "priceUsed": 4750.00,
        "priceType": "LOCAL_MANDI_MODAL",
        "mandiName": "Bhuj Mandi",
        "latestArrivalDate": "2026-10-24",
        "daysOld": 1,
        "freshnessStatus": "FRESH",
        "mspPrice": 4892.00,
        "priceSpreadRatio": 0.18,
        "explanation": "Local mandi reported active modal transactions 1 day ago. Market price is stable within 18% spread."
      },
      "economicProjection": {
        "allocatedHectares": 2.5000,
        "totalEstimatedCostInr": 80000.00,
        "yieldRangeQuintalPerHa": {
          "conservative": 14.50,
          "modal": 19.00,
          "optimistic": 23.50
        },
        "totalProductionQuintals": {
          "conservative": 36.25,
          "modal": 47.50,
          "optimistic": 58.75
        },
        "grossWholesaleRevenueInr": {
          "conservative": 177335.00,
          "modal": 225625.00,
          "optimistic": 279062.50
        },
        "grossMarginInr": {
          "conservative": 97335.00,
          "modal": 145625.00,
          "optimistic": 199062.50
        },
        "grossMarginPerHectareInr": {
          "conservative": 38934.00,
          "modal": 58250.00,
          "optimistic": 79625.00
        },
        "statutoryDisclaimer": "Estimates represent gross wholesale realization at the mandi gate. Figures do not constitute a financial profit or yield guarantee.",
        "excludedCostItems": [
          "Post-harvest transport to mandi (estimated ₹60–₹120/Q)",
          "Mandi user cess and market fee (1–2%)",
          "Loading, unloading, and weighing fees",
          "Family labor opportunity cost",
          "Land rent or lease depreciation"
        ]
      },
      "risks": [
        {
          "category": "WEATHER",
          "severity": "MEDIUM",
          "title": "Waterlogging Risk During Pod Formation",
          "description": "Excess rainfall during pod filling can reduce soybean seed quality in heavy black soils.",
          "mitigationAdvice": "Ensure field drainage channels are cleared before peak monsoon storms."
        }
      ],
      "explanations": [
        "Strong agronomic fit for black soil with full financial feasibility.",
        "Positive gross margins across all three yield and price scenarios."
      ],
      "dataQualityWarnings": []
    }
  ],
  "conditionallyFeasibleCrops": [
    {
      "cropId": 8,
      "cropName": "Cotton (Bt)",
      "botanicalName": "Gossypium hirsutum",
      "category": "COMMERCIAL",
      "overallStatus": "CONDITIONALLY_FEASIBLE",
      "agronomicSuitability": {
        "status": "HIGHLY_SUITABLE",
        "soilFit": {
          "status": "HIGH",
          "soilType": "BLACK",
          "notes": "Deep black soils offer excellent root anchorage and moisture for cotton."
        },
        "waterFit": {
          "status": "HIGH",
          "requiredMonths": 6,
          "availableMonths": 8,
          "waterRequirementMm": "700 - 1100 mm",
          "notes": "Available water duration is sufficient."
        },
        "seasonFit": {
          "status": "HIGH",
          "targetSeason": "KHARIF",
          "sowingWindow": "May - June"
        },
        "climateFit": {
          "status": "HIGH",
          "temperatureTolerance": "Favorable",
          "heatStressRisk": false
        },
        "factorExplanations": [
          "Excellent agronomic suitability on black soil."
        ]
      },
      "financialFeasibility": {
        "status": "PARTIALLY_FEASIBLE",
        "costPerHectareA2": 58000.00,
        "requiredWorkingCapitalFullParcel": 145000.00,
        "availableWorkingCapital": 120000.00,
        "capitalSufficiencyRatio": 0.83,
        "affordableHectares": 2.0689,
        "isCapitalConstrained": true,
        "riskAlignment": "HIGH_RISK_MISMATCH",
        "explanation": "Working capital (₹1,20,000) is insufficient for full 2.5 ha (requires ₹1,45,000). Crop is feasible on an allocated 2.06 ha plot."
      },
      "marketAttractiveness": {
        "status": "MODERATE",
        "priceUsed": 7100.00,
        "priceType": "LOCAL_MANDI_MODAL",
        "mandiName": "Anjar Mandi",
        "latestArrivalDate": "2026-10-18",
        "daysOld": 7,
        "freshnessStatus": "FRESH",
        "mspPrice": 7121.00,
        "priceSpreadRatio": 0.28,
        "explanation": "Latest modal price is marginally below MSP floor (₹7,121). Market spread reflects moderate price volatility."
      },
      "economicProjection": {
        "allocatedHectares": 2.0689,
        "totalEstimatedCostInr": 120000.00,
        "yieldRangeQuintalPerHa": {
          "conservative": 15.00,
          "modal": 21.00,
          "optimistic": 26.00
        },
        "totalProductionQuintals": {
          "conservative": 31.03,
          "modal": 43.45,
          "optimistic": 53.79
        },
        "grossWholesaleRevenueInr": {
          "conservative": 220933.00,
          "modal": 308495.00,
          "optimistic": 381909.00
        },
        "grossMarginInr": {
          "conservative": 100933.00,
          "modal": 188495.00,
          "optimistic": 261909.00
        },
        "grossMarginPerHectareInr": {
          "conservative": 48785.00,
          "modal": 91108.00,
          "optimistic": 126593.00
        },
        "statutoryDisclaimer": "Estimates represent gross wholesale realization at the mandi gate. Figures do not constitute a financial profit or yield guarantee.",
        "excludedCostItems": [
          "Post-harvest transport to mandi",
          "Mandi market fee",
          "Picking labor premiums during peak harvest"
        ]
      },
      "risks": [
        {
          "category": "FINANCIAL",
          "severity": "HIGH",
          "title": "Capital Exhaustion Risk",
          "description": "Cultivating beyond 2.06 hectares risks cash-flow exhaustion before harvest.",
          "mitigationAdvice": "Restrict planting to 2.00 ha or arrange institutional Kisan Credit Card (KCC) credit."
        }
      ],
      "explanations": [
        "Agronomically well-suited, but restricted by working capital to 2.06 ha."
      ],
      "dataQualityWarnings": [
        {
          "code": "CAPITAL_CONSTRAINED_ALLOCATION",
          "field": "allocatedHectares",
          "severity": "WARNING",
          "message": "Land allocation scaled down from 2.50 ha to 2.06 ha to match available working capital.",
          "provenanceNotice": "Computed dynamically from workingCapitalInr."
        }
      ]
    }
  ],
  "excludedCrops": [
    {
      "cropId": 2,
      "cropName": "Wheat",
      "botanicalName": "Triticum aestivum",
      "category": "CEREAL",
      "overallStatus": "INFEASIBLE_EXCLUDED",
      "disqualificationCode": "OFF_SEASON",
      "disqualificationReason": "Wheat is a winter Rabi crop and cannot be sown during target season KHARIF (sowing window is November - December)."
    }
  ],
  "attribution": {
    "marketDataProvider": "data.gov.in (AGMARKNET Daily Mandi Prices)",
    "marketLicense": "Government Open Data License - India (GODL)",
    "marketLicenseUrl": "https://data.gov.in/sites/default/files/Gazette_Notification_OGDL.pdf",
    "weatherDataProvider": "Open-Meteo.com",
    "weatherLicense": "CC BY 4.0",
    "weatherLicenseUrl": "https://open-meteo.com",
    "disclaimer": "Agricultural recommendations and economic models are indicative only and do not constitute agronomic guarantees or financial advice."
  }
}
```

---

### 2.2. Error Responses & Handling Matrix

| HTTP Status | Error Reason | Condition | Response Payload Example |
|---|---|---|---|
| `400 Bad Request` | `Validation failed` | Negative `workingCapitalOverride`, negative/zero `cultivableHectaresOverride`, or invalid `targetSeason`. | `{"timestamp": "...", "status": 400, "error": "Bad Request", "message": "Validation failed", "fieldErrors": [{"field": "cultivableHectaresOverride", "message": "Cultivable hectares must be greater than zero"}]}` |
| `401 Unauthorized` | `Unauthorized` | Missing or expired JWT Bearer token in request header. | `{"status": 401, "error": "Unauthorized", "message": "Full authentication is required to access this resource"}` |
| `403 Forbidden` | `Forbidden` | Authenticated `UserPrincipal.getId()` does not match `{userId}` in URL path. | `{"status": 403, "error": "Forbidden", "message": "You are not authorized to access or modify this farmer profile"}` |
| `404 Not Found` | `Not Found` | Specified `userId` or linked `FarmerProfile` does not exist in the database. | `{"status": 404, "error": "Not Found", "message": "FarmerProfile for user id 1 was not found"}` |
| `422 Unprocessable Entity` | `Unprocessable Entity` | Profile exists but lacks essential decision data: `cultivableHectares` is null/zero, or `primaryState`/`primaryDistrict` is missing. | `{"status": 422, "error": "Unprocessable Entity", "message": "Farmer profile requires cultivable land area and primary district before strategy evaluation can be executed"}` |

---

### 2.3. Auxiliary Reference Endpoints (Read-Only)

#### `GET /api/v1/strategy/reference-crops`
Returns the catalog of active crops supported by the strategy engine, filtered optionally by season or category.

**Access:** Public / Authenticated  
**Query Parameters:**
- `season`: Optional string (`KHARIF`, `RABI`, `ZAID`)
- `category`: Optional string (`CEREAL`, `PULSE`, `OILSEED`, `COMMERCIAL`)

**Response (`200 OK`):**
```json
[
  {
    "id": 5,
    "canonicalName": "Soybean",
    "botanicalName": "Glycine max",
    "category": "OILSEED",
    "marketCommodityName": "Soyabean",
    "supportedSeasons": ["KHARIF"],
    "typicalDurationDays": "100 - 120 days"
  }
]
```
