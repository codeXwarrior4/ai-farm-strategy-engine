-- ============================================================
-- V10: Add Source Classification Provenance to Market Price Records
-- AI Farm Strategy & Decision Engine
-- ============================================================
-- Design notes:
--   * Distinguishes official AGMARKNET API observations (OFFICIAL_API)
--     from demonstration seeded data (DEMO_SEEDED) and manual verifications.
--   * Existing V9 seeded records are categorized as DEMO_SEEDED.
--   * Default is DEMO_SEEDED to safeguard unclassified records.
-- ============================================================

ALTER TABLE market_price_records
    ADD COLUMN source_classification VARCHAR(30) NOT NULL DEFAULT 'DEMO_SEEDED';

-- Ensure all existing records are marked as DEMO_SEEDED
UPDATE market_price_records
SET source_classification = 'DEMO_SEEDED'
WHERE source_classification IS NULL OR source_classification = '';

-- Add check constraint for valid source classifications
ALTER TABLE market_price_records
    ADD CONSTRAINT chk_mpr_source_classification
    CHECK (source_classification IN ('OFFICIAL_API', 'DEMO_SEEDED', 'MANUAL_VERIFIED'));

-- Add index for provenance-scoped queries
CREATE INDEX idx_mpr_provenance
    ON market_price_records (state, district, commodity, source_classification, arrival_date);

