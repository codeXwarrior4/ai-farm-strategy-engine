-- ============================================================
-- V4: Add farm coordinates to farmer_profiles - Phase 2B.1
-- AI Farm Strategy & Decision Engine
-- ============================================================

ALTER TABLE farmer_profiles
    ADD COLUMN latitude     DECIMAL(9, 6)   NULL
        COMMENT 'Farm latitude coordinate in decimal degrees (-90.0 to 90.0)',
    ADD COLUMN longitude    DECIMAL(9, 6)   NULL
        COMMENT 'Farm longitude coordinate in decimal degrees (-180.0 to 180.0)';

ALTER TABLE farmer_profiles
    ADD CONSTRAINT chk_fp_latitude
        CHECK (latitude IS NULL OR (latitude >= -90.0 AND latitude <= 90.0)),
    ADD CONSTRAINT chk_fp_longitude
        CHECK (longitude IS NULL OR (longitude >= -180.0 AND longitude <= 180.0)),
    ADD CONSTRAINT chk_fp_coordinates_paired
        CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL));
