-- ============================================================
-- V4: Add farm coordinates to farmer_profiles - Phase 2B.1 (H2-compatible)
-- ============================================================

ALTER TABLE farmer_profiles
    ADD COLUMN latitude     DECIMAL(9, 6)   NULL;

ALTER TABLE farmer_profiles
    ADD COLUMN longitude    DECIMAL(9, 6)   NULL;
