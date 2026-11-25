-- Administração de identidade (ADR-027).
--
-- A senha temporária é entregue uma única vez pela TI e exige troca no
-- primeiro acesso. O prazo é curto e a coluna `must_change_password` bloqueia
-- as demais rotas até a troca. Nenhum backfill além do default: as contas
-- existentes continuam válidas.

ALTER TABLE operational_users
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS temporary_password_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN operational_users.must_change_password IS
    'Senha temporária; todas as rotas de negócio respondem 403 até POST /api/v1/auth/password.';
COMMENT ON COLUMN operational_users.temporary_password_expires_at IS
    'Fim da validade da senha temporária (24 h por padrão). Expirada, exige reset pela TI.';
COMMENT ON COLUMN operational_users.password_changed_at IS
    'Última troca de senha feita pelo próprio operador.';

-- Cursor estável da lista da TI: a sequência de criação sobrevive a mudanças
-- de papel/estado e não depende de ordenar UUID. `created_at` continua sendo a
-- data exibida; `seq` é só a chave da paginação.
ALTER TABLE operational_users
    ADD COLUMN IF NOT EXISTS seq BIGSERIAL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_operational_users_seq
    ON operational_users (seq);

CREATE INDEX IF NOT EXISTS idx_operational_users_active_seq
    ON operational_users (active, seq DESC);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_last_used
    ON auth_sessions (user_id, last_used_at DESC);
