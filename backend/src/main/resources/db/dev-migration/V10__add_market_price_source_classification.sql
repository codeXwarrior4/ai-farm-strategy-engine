-- ============================================================
-- V10: Add Source Classification Provenance to Market Price Records
-- AI Farm Strategy & Decision Engine
-- ============================================================

ALTER TABLE market_price_records
    ADD COLUMN source_classification VARCHAR(30) NOT NULL DEFAULT 'DEMO_SEEDED';

UPDATE market_price_records
SET source_classification = 'DEMO_SEEDED'
WHERE source_classification IS NULL OR source_classification = '';

ALTER TABLE market_price_records
    ADD CONSTRAINT chk_mpr_source_classification
    CHECK (source_classification IN ('OFFICIAL_API', 'DEMO_SEEDED', 'MANUAL_VERIFIED'));

CREATE INDEX idx_mpr_provenance
    ON market_price_records (state, district, commodity, source_classification, arrival_date);

