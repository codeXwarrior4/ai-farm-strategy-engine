-- ============================================================
-- V3: Extend farmer_profiles — Phase 2A (H2 Test-compatible)
-- ============================================================
-- H2 does not support COMMENT on columns or multi-constraint
-- ALTER TABLE in the same statement as column additions.
-- CHECK constraints are omitted: H2 silently ignores them in
-- MySQL mode and Jakarta Validation enforces them at app level.
-- ============================================================

ALTER TABLE farmer_profiles
    ADD COLUMN cultivable_hectares          DECIMAL(10, 4)  NULL;

ALTER TABLE farmer_profiles
    ADD COLUMN soil_type                    VARCHAR(30)     NULL;

ALTER TABLE farmer_profiles
    ADD COLUMN irrigation_source            VARCHAR(30)     NULL;

ALTER TABLE farmer_profiles
    ADD COLUMN water_availability_months    TINYINT         NULL;

ALTER TABLE farmer_profiles
    ADD COLUMN working_capital_inr          DECIMAL(14, 2)  NULL;

ALTER TABLE farmer_profiles
    ADD COLUMN risk_tolerance               VARCHAR(10)     NULL;
