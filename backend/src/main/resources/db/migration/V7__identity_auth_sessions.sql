-- Sessões de refresh (ADR-009): token opaco no cookie HttpOnly, hash no banco,
-- rotação por família e detecção de reuso.
--
-- A matrícula é identificador funcional de login/exibição e não é segredo, então
-- fica em claro; nome, e-mail e CPF continuam cifrados em operational_users.

ALTER TABLE operational_users
    ADD COLUMN IF NOT EXISTS badge_number VARCHAR(32);

COMMENT ON COLUMN operational_users.badge_number IS
    'Matrícula funcional (ex.: BM-10492). Identificador de login/exibição, não é segredo.';

CREATE UNIQUE INDEX IF NOT EXISTS uq_operational_users_badge_number
    ON operational_users (badge_number)
    WHERE badge_number IS NOT NULL;

CREATE TABLE IF NOT EXISTS auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES operational_users (id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    refresh_hash VARCHAR(64) NOT NULL UNIQUE,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by_id UUID,
    user_agent VARCHAR(256),
    ip_bidx VARCHAR(64)
);

COMMENT ON TABLE auth_sessions IS
    'Sessões de refresh opacas. Guarda apenas o hash SHA-256 do token; nunca o valor em claro.';
COMMENT ON COLUMN auth_sessions.refresh_hash IS
    'SHA-256 em base64url do token opaco. Token com 256 bits de entropia: hash simples é suficiente.';
COMMENT ON COLUMN auth_sessions.family_id IS
    'Família de rotações originada por um login; reuso de token revogado revoga a família inteira.';
COMMENT ON COLUMN auth_sessions.replaced_by_id IS
    'Sucessor emitido na rotação; junto de revoked_at sustenta a janela de tolerância multi-aba.';
COMMENT ON COLUMN auth_sessions.ip_bidx IS
    'Índice cego (HMAC) do IP de origem. O IP em claro não é persistido.';

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id ON auth_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_family_id ON auth_sessions (family_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_active
    ON auth_sessions (expires_at)
    WHERE revoked_at IS NULL;
