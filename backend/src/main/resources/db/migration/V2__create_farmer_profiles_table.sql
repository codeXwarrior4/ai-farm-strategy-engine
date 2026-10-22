-- ============================================================
-- V2: Create farmer_profiles table
-- Phase 1 — AI Farm Strategy & Decision Engine
-- ============================================================
-- Design notes:
--   * owner_id links to users(id) — one user may own one profile initially.
--   * total_land_hectares is nullable; user may not know it yet.
--   * primary_state / primary_district provided for regional context.
--   * This table is intentionally minimal; future phases will add
--     farms, soil profiles, water availability, financial constraints,
--     crops, strategies and decision audit records as separate tables.
-- ============================================================

CREATE TABLE farmer_profiles (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    owner_id                BIGINT          NOT NULL,
    full_name               VARCHAR(200)    NOT NULL,
    phone_number            VARCHAR(20)             COMMENT 'E.164 format preferred',
    primary_state           VARCHAR(100),
    primary_district        VARCHAR(100),
    total_land_hectares     DECIMAL(10, 4)          COMMENT 'Approximate total landholding',
    years_of_experience     INT             NOT NULL DEFAULT 0,
    preferred_language      VARCHAR(10)     NOT NULL DEFAULT 'en',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_farmer_profiles           PRIMARY KEY (id),
    CONSTRAINT fk_fp_owner                  FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_fp_owner                  UNIQUE (owner_id),
    CONSTRAINT chk_fp_years_exp             CHECK (years_of_experience >= 0),
    CONSTRAINT chk_fp_land_hectares         CHECK (total_land_hectares IS NULL OR total_land_hectares > 0)
) ENGINE=InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
