-- ============================================================
-- V10: Add Source Classification Provenance to Market Price Records (H2 Test-compatible)
-- AI Farm Strategy & Decision Engine
-- ============================================================

ALTER TABLE market_price_records
    ADD COLUMN source_classification VARCHAR(30) DEFAULT 'DEMO_SEEDED' NOT NULL;

UPDATE market_price_records
SET source_classification = 'DEMO_SEEDED'
WHERE source_classification IS NULL OR source_classification = '';

CREATE INDEX IF NOT EXISTS idx_mpr_provenance
    ON market_price_records (state, district, commodity, source_classification, arrival_date);

