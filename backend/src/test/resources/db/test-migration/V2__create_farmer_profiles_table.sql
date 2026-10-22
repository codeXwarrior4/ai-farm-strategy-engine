-- ============================================================
-- V2: Create farmer_profiles table (H2-compatible version for tests)
-- ============================================================

CREATE TABLE IF NOT EXISTS farmer_profiles (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    owner_id                BIGINT          NOT NULL,
    full_name               VARCHAR(200)    NOT NULL,
    phone_number            VARCHAR(20),
    primary_state           VARCHAR(100),
    primary_district        VARCHAR(100),
    total_land_hectares     DECIMAL(10, 4),
    years_of_experience     INT             NOT NULL DEFAULT 0,
    preferred_language      VARCHAR(10)     NOT NULL DEFAULT 'en',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_farmer_profiles   PRIMARY KEY (id),
    CONSTRAINT fk_fp_owner          FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_fp_owner          UNIQUE (owner_id)
);
