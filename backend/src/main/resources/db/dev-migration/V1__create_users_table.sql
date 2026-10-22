-- ============================================================
-- V1: Create users table (H2 Dev / In-Memory compatible)
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    username    VARCHAR(100)    NOT NULL,
    email       VARCHAR(255)    NOT NULL,
    password    VARCHAR(255)    NOT NULL,
    role        VARCHAR(50)     NOT NULL DEFAULT 'ROLE_FARMER',
    enabled     BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_users         PRIMARY KEY (id),
    CONSTRAINT uq_users_email   UNIQUE (email),
    CONSTRAINT uq_users_name    UNIQUE (username)
);
