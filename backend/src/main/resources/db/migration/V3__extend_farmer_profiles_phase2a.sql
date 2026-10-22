-- ============================================================
-- V3: Extend farmer_profiles — Phase 2A Farm Data Foundation
-- AI Farm Strategy & Decision Engine
-- ============================================================
-- Adds agronomic and financial context columns to the existing
-- farmer_profiles table. All columns are nullable so existing
-- rows are unaffected and all fields remain optional on the API.
--
-- Column notes:
--   cultivable_hectares       — net cultivable portion of total landholding
--   soil_type                 — soil classification (stored as enum name)
--   irrigation_source         — primary irrigation method (stored as enum name)
--   water_availability_months — months per year with reliable water (0–12)
--   working_capital_inr       — available operational budget in INR
--   risk_tolerance            — self-reported risk appetite (LOW/MEDIUM/HIGH)
-- ============================================================

ALTER TABLE farmer_profiles
    ADD COLUMN cultivable_hectares          DECIMAL(10, 4)  NULL
        COMMENT 'Net cultivable area in hectares; must be > 0 if provided',
    ADD COLUMN soil_type                    VARCHAR(30)     NULL
        COMMENT 'Soil classification: ALLUVIAL, BLACK, RED, LATERITE, SANDY, LOAMY, CLAYEY, OTHER',
    ADD COLUMN irrigation_source            VARCHAR(30)     NULL
        COMMENT 'Primary irrigation: BOREWELL, OPEN_WELL, CANAL, RIVER, RAIN_FED, TANK, DRIP, SPRINKLER, OTHER',
    ADD COLUMN water_availability_months    TINYINT         NULL
        COMMENT 'Months per year with reliable water access (0–12)',
    ADD COLUMN working_capital_inr          DECIMAL(14, 2)  NULL
        COMMENT 'Available operational budget in Indian Rupees',
    ADD COLUMN risk_tolerance               VARCHAR(10)     NULL
        COMMENT 'Self-reported risk appetite: LOW, MEDIUM, HIGH';

-- CHECK constraints enforce domain rules at the database level.
-- Application-level validation (Jakarta) enforces the same rules
-- before reaching the database, so these are a safety net only.
ALTER TABLE farmer_profiles
    ADD CONSTRAINT chk_fp_cultivable_ha
        CHECK (cultivable_hectares IS NULL OR cultivable_hectares > 0),
    ADD CONSTRAINT chk_fp_water_months
        CHECK (water_availability_months IS NULL
            OR (water_availability_months >= 0 AND water_availability_months <= 12)),
    ADD CONSTRAINT chk_fp_working_cap
        CHECK (working_capital_inr IS NULL OR working_capital_inr >= 0);
