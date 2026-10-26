-- ============================================================
-- V5: Create market_price_records table (H2 Dev-compatible) - Phase 2B.2 Stage 1
-- AI Farm Strategy & Decision Engine
-- ============================================================

CREATE TABLE IF NOT EXISTS market_price_records (
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
    CONSTRAINT chk_mpr_min_lte_max          CHECK (min_price <= max_price)
);

CREATE INDEX IF NOT EXISTS idx_mpr_lookup ON market_price_records (commodity, state, district, market, arrival_date);
CREATE INDEX IF NOT EXISTS idx_mpr_arrival_date ON market_price_records (arrival_date);
