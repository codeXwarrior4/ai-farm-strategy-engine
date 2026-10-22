-- ============================================================
-- V1: Create users table
-- Phase 1 — AI Farm Strategy & Decision Engine
-- ============================================================

CREATE TABLE users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    username    VARCHAR(100)    NOT NULL,
    email       VARCHAR(255)    NOT NULL,
    password    VARCHAR(255)    NOT NULL COMMENT 'BCrypt hash — never plaintext',
    role        VARCHAR(50)     NOT NULL DEFAULT 'ROLE_FARMER',
    enabled     BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_users         PRIMARY KEY (id),
    CONSTRAINT uq_users_email   UNIQUE (email),
    CONSTRAINT uq_users_name    UNIQUE (username)
) ENGINE=InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
