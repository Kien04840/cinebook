-- ========================================================================
-- PHẦN 1: MIGRATION V1_1 (Phase 1: Email Verification)
-- File: src/main/resources/db/migration/V1_1__add_email_verification_tokens.sql
-- ========================================================================
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    token VARCHAR(100) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_tokens_token UNIQUE (token),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) 
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens (user_id);
CREATE INDEX idx_email_verification_tokens_expires ON email_verification_tokens (expires_at);

