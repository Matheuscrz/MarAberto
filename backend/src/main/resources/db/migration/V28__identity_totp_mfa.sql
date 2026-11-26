-- MFA via TOTP (RFC 6238) e códigos de recuperação de uso único para
-- perfis privilegiados (COORDINATOR e ADMIN).

ALTER TABLE operational_users
    ADD COLUMN totp_secret VARCHAR(512),
    ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_enrolled_at TIMESTAMPTZ;

CREATE TABLE user_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES operational_users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_recovery_code UNIQUE (user_id, code_hash)
);

CREATE INDEX idx_user_recovery_codes_user_unused
    ON user_recovery_codes (user_id)
    WHERE used_at IS NULL;
