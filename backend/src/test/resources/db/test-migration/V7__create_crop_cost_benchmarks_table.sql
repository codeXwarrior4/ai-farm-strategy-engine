-- ============================================================
-- V7: Create crop_cost_benchmarks table (H2 Test-compatible) - Phase 3 Stage 4B
-- AI Farm Strategy & Decision Engine
-- ============================================================

CREATE TABLE IF NOT EXISTS crop_cost_benchmarks (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    crop_id                 BIGINT          NOT NULL,
    state                   VARCHAR(100)    NOT NULL,
    season                  VARCHAR(30)     NOT NULL,
    benchmark_year          INT             NOT NULL,
    reference_period        VARCHAR(50)     NOT NULL,
    cost_basis              VARCHAR(30)     NOT NULL DEFAULT 'A2',
    cost_unit               VARCHAR(30)     NOT NULL DEFAULT 'INR_PER_HA',
    currency                VARCHAR(10)     NOT NULL DEFAULT 'INR',
    cost_per_hectare_a2     DECIMAL(12, 2)  NOT NULL,
    cost_per_hectare_a2fl   DECIMAL(12, 2)  NULL,
    cost_per_hectare_c2     DECIMAL(12, 2)  NULL,
    crop_source_label       VARCHAR(150)    NULL,
    evidence_tier           VARCHAR(30)     NOT NULL DEFAULT 'VERIFIED_DIRECT',
    data_nature             VARCHAR(30)     NOT NULL DEFAULT 'OBSERVED_SURVEY',
    source_organization     VARCHAR(150)    NOT NULL,
    source_publication      VARCHAR(255)    NOT NULL,
    source_url              VARCHAR(500)    NULL,
    source_table_ref        VARCHAR(100)    NULL,
    revision_version        INT             NOT NULL DEFAULT 1,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    active_flag             TINYINT         NULL DEFAULT 1,
    superseded_by_id        BIGINT          NULL,
    notes                   VARCHAR(1000)   NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crop_cost_benchmarks PRIMARY KEY (id),
    CONSTRAINT fk_ccb_crop_id FOREIGN KEY (crop_id) REFERENCES crop_master(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ccb_superseded_by FOREIGN KEY (superseded_by_id) REFERENCES crop_cost_benchmarks(id) ON DELETE SET NULL,
    CONSTRAINT uq_ccb_natural_identity UNIQUE (crop_id, state, season, benchmark_year, source_organization, revision_version),
    CONSTRAINT uq_ccb_single_active UNIQUE (crop_id, state, season, benchmark_year, source_organization, active_flag),
    CONSTRAINT chk_ccb_cost_a2 CHECK (cost_per_hectare_a2 > 0),
    CONSTRAINT chk_ccb_cost_a2fl CHECK (cost_per_hectare_a2fl IS NULL OR cost_per_hectare_a2fl > 0),
    CONSTRAINT chk_ccb_cost_c2 CHECK (cost_per_hectare_c2 IS NULL OR cost_per_hectare_c2 > 0),
    CONSTRAINT chk_ccb_cost_basis CHECK (cost_basis IN ('A2')),
    CONSTRAINT chk_ccb_cost_unit CHECK (cost_unit IN ('INR_PER_HA')),
    CONSTRAINT chk_ccb_currency CHECK (currency IN ('INR')),
    CONSTRAINT chk_ccb_active_flag CHECK ((is_active = TRUE AND active_flag = 1) OR (is_active = FALSE AND active_flag IS NULL)),
    CONSTRAINT chk_ccb_evidence_consistency CHECK ((evidence_tier = 'VERIFIED_DIRECT' AND data_nature = 'OBSERVED_SURVEY') OR (evidence_tier = 'UNVERIFIED')),
    CONSTRAINT chk_ccb_season CHECK (season IN ('KHARIF', 'RABI', 'ZAID', 'ALL_SEASONS', 'WHOLE_YEAR', 'PERENNIAL'))
);

CREATE INDEX IF NOT EXISTS idx_ccb_lookup ON crop_cost_benchmarks (crop_id, state, season, is_active);
