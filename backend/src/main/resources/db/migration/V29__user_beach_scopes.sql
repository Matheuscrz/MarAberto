-- F-06: Escopo territorial opcional por usuário (ex.: coordenadores regionais
-- em instalações com múltiplos batalhões). Usuário sem linhas nesta tabela
-- mantém visão irrestrita (compatível com instalações de comando único).

CREATE TABLE user_beach_scopes (
    user_id    UUID        NOT NULL REFERENCES operational_users(id) ON DELETE CASCADE,
    beach_id   UUID        NOT NULL REFERENCES beaches(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, beach_id)
);

CREATE INDEX idx_user_beach_scopes_beach ON user_beach_scopes (beach_id);
