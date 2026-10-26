-- ============================================================
-- V5: Create market_price_records table - Phase 2B.2 Stage 1
-- AI Farm Strategy & Decision Engine
-- ============================================================
-- Design notes:
--   * Stores daily mandi market price records from government sources (e.g. data.gov.in / Agmarknet).
--   * Preserves raw price semantics: modal_price is nullable and never defaulted to 0.
--   * Commodity and market names are kept as source strings to prevent loss of valid government records.
--   * Deduplication uses a deterministic 64-character SHA-256 hash (deduplication_key) calculated
--     over normalized (state, district, market, commodity, variety, grade, arrival_date).
--     This avoids database-engine-specific NULL handling in composite unique constraints.
--   * Explicit price_unit defaults to 'INR_PER_QUINTAL', currency defaults to 'INR'.
-- ============================================================

CREATE TABLE market_price_records (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    state                   VARCHAR(100)    NOT NULL,
    district                VARCHAR(100)    NOT NULL,
    market                  VARCHAR(150)    NOT NULL,
    commodity               VARCHAR(150)    NOT NULL,
    variety                 VARCHAR(100)    NULL,
    grade                   VARCHAR(50)     NULL,
    arrival_date            DATE            NOT NULL,
    min_price               DECIMAL(12, 2)  NOT NULL,
    max_price               DECIMAL(12, 2)  NOT NULL,
    modal_price             DECIMAL(12, 2)  NULL,
    price_unit              VARCHAR(50)     NOT NULL DEFAULT 'INR_PER_QUINTAL',
    currency                VARCHAR(10)     NOT NULL DEFAULT 'INR',
    source_provider         VARCHAR(100)    NOT NULL,
    source_record_id        VARCHAR(255)    NULL,
    retrieval_timestamp     DATETIME        NOT NULL,
    deduplication_key       VARCHAR(64)     NOT NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_market_price_records      PRIMARY KEY (id),
    CONSTRAINT uq_mpr_deduplication_key     UNIQUE (deduplication_key),
    CONSTRAINT chk_mpr_min_price            CHECK (min_price >= 0),
    CONSTRAINT chk_mpr_max_price            CHECK (max_price >= 0),
    CONSTRAINT chk_mpr_modal_price          CHECK (modal_price IS NULL OR modal_price >= 0),
    CONSTRAINT chk_mpr_min_lte_max          CHECK (min_price <= max_price),
    INDEX idx_mpr_lookup (commodity, state, district, market, arrival_date),
    INDEX idx_mpr_arrival_date (arrival_date)
) ENGINE=InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
