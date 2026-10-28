-- ============================================================
-- V8: Create crop_yield_benchmarks and crop_msp_floors tables (H2 Test-compatible) - Phase 4B
-- AI Farm Strategy & Decision Engine
-- ============================================================

CREATE TABLE IF NOT EXISTS crop_yield_benchmarks (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    crop_id                     BIGINT          NOT NULL,
    state                       VARCHAR(100)    NOT NULL,
    season                      VARCHAR(30)     NOT NULL,
    benchmark_year              INT             NOT NULL,
    reference_period            VARCHAR(50)     NOT NULL,
    yield_quintals_per_hectare  DECIMAL(10, 2)  NOT NULL,
    yield_unit                  VARCHAR(30)     NOT NULL DEFAULT 'QUINTAL_PER_HA',
    crop_source_label           VARCHAR(150)    NULL,
    evidence_tier               VARCHAR(30)     NOT NULL DEFAULT 'VERIFIED_DIRECT',
    data_nature                 VARCHAR(30)     NOT NULL DEFAULT 'OFFICIAL_STATISTICS',
    source_organization         VARCHAR(150)    NOT NULL,
    source_publication          VARCHAR(255)    NOT NULL,
    source_url                  VARCHAR(500)    NULL,
    source_table_ref            VARCHAR(100)    NULL,
    revision_version            INT             NOT NULL DEFAULT 1,
    is_active                   BOOLEAN         NOT NULL DEFAULT TRUE,
    active_flag                 TINYINT         NULL DEFAULT 1,
    superseded_by_id            BIGINT          NULL,
    notes                       VARCHAR(1000)   NULL,
    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crop_yield_benchmarks PRIMARY KEY (id),
    CONSTRAINT fk_cyb_crop_id FOREIGN KEY (crop_id) REFERENCES crop_master(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cyb_superseded_by FOREIGN KEY (superseded_by_id) REFERENCES crop_yield_benchmarks(id) ON DELETE SET NULL,
    CONSTRAINT uq_cyb_natural_identity UNIQUE (crop_id, state, season, benchmark_year, source_organization, revision_version),
    CONSTRAINT uq_cyb_single_active UNIQUE (crop_id, state, season, benchmark_year, source_organization, active_flag),
    CONSTRAINT chk_cyb_yield CHECK (yield_quintals_per_hectare > 0),
    CONSTRAINT chk_cyb_yield_unit CHECK (yield_unit IN ('QUINTAL_PER_HA')),
    CONSTRAINT chk_cyb_season CHECK (season IN ('KHARIF', 'RABI', 'ZAID', 'ALL_SEASONS', 'WHOLE_YEAR', 'PERENNIAL')),
    CONSTRAINT chk_cyb_active_flag CHECK ((is_active = TRUE AND active_flag = 1) OR (is_active = FALSE AND active_flag IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_cyb_lookup ON crop_yield_benchmarks (crop_id, state, season, is_active);

CREATE TABLE IF NOT EXISTS crop_msp_floors (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    crop_id                     BIGINT          NOT NULL,
    season                      VARCHAR(30)     NOT NULL,
    crop_year                   INT             NOT NULL,
    marketing_season_label      VARCHAR(50)     NOT NULL,
    state_scope                 VARCHAR(100)    NOT NULL DEFAULT 'ALL_INDIA',
    variety_or_grade            VARCHAR(100)    NOT NULL DEFAULT 'COMMON',
    msp_inr_per_quintal         DECIMAL(12, 2)  NOT NULL,
    price_unit                  VARCHAR(30)     NOT NULL DEFAULT 'INR_PER_QUINTAL',
    currency                    VARCHAR(10)     NOT NULL DEFAULT 'INR',
    evidence_tier               VARCHAR(30)     NOT NULL DEFAULT 'OFFICIAL_STATUTORY',
    source_organization         VARCHAR(150)    NOT NULL DEFAULT 'CACP-MoA&FW',
    source_publication          VARCHAR(255)    NOT NULL,
    source_url                  VARCHAR(500)    NULL,
    source_notification_ref     VARCHAR(100)    NULL,
    revision_version            INT             NOT NULL DEFAULT 1,
    is_active                   BOOLEAN         NOT NULL DEFAULT TRUE,
    active_flag                 TINYINT         NULL DEFAULT 1,
    superseded_by_id            BIGINT          NULL,
    notes                       VARCHAR(1000)   NULL,
    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crop_msp_floors PRIMARY KEY (id),
    CONSTRAINT fk_cmf_crop_id FOREIGN KEY (crop_id) REFERENCES crop_master(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cmf_superseded_by FOREIGN KEY (superseded_by_id) REFERENCES crop_msp_floors(id) ON DELETE SET NULL,
    CONSTRAINT uq_cmf_natural_identity UNIQUE (crop_id, state_scope, season, crop_year, variety_or_grade, revision_version),
    CONSTRAINT uq_cmf_single_active UNIQUE (crop_id, state_scope, season, crop_year, variety_or_grade, active_flag),
    CONSTRAINT chk_cmf_msp CHECK (msp_inr_per_quintal > 0),
    CONSTRAINT chk_cmf_price_unit CHECK (price_unit IN ('INR_PER_QUINTAL')),
    CONSTRAINT chk_cmf_currency CHECK (currency IN ('INR')),
    CONSTRAINT chk_cmf_season CHECK (season IN ('KHARIF', 'RABI', 'ZAID', 'ALL_SEASONS', 'WHOLE_YEAR')),
    CONSTRAINT chk_cmf_active_flag CHECK ((is_active = TRUE AND active_flag = 1) OR (is_active = FALSE AND active_flag IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_cmf_lookup ON crop_msp_floors (crop_id, state_scope, season, crop_year, is_active);
