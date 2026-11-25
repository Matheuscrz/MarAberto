-- Trilha de auditoria append-only com encadeamento de hash (RNF06, ADR-027).
--
-- A aplicação só insere. O trigger abaixo recusa UPDATE/DELETE até para o
-- papel dono da tabela; a integridade é verificada pelo encadeamento
-- prev_hash/hash e por consulta exclusiva de ADMIN.
--
-- `detail` é JSONB sem PII: ids, nomes de campo e valores não sensíveis.
-- `ip_bidx` é o índice cego do IP (nunca o IP em claro).

CREATE TABLE IF NOT EXISTS audit_log (
    id           BIGSERIAL PRIMARY KEY,
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_id     UUID,
    actor_role   VARCHAR(32),
    action       VARCHAR(60) NOT NULL,
    entity_type  VARCHAR(40) NOT NULL,
    entity_id    VARCHAR(64),
    trace_id     VARCHAR(64),
    ip_bidx      VARCHAR(64),
    detail       JSONB NOT NULL DEFAULT '{}'::jsonb,
    prev_hash    CHAR(64) NOT NULL,
    hash         CHAR(64) NOT NULL
);

COMMENT ON TABLE audit_log IS
    'Trilha imutável; cada linha encadeia a anterior por SHA-256. Sem UPDATE/DELETE.';
COMMENT ON COLUMN audit_log.detail IS
    'Detalhe sem PII: ids, nomes de campo e valores não sensíveis.';
COMMENT ON COLUMN audit_log.prev_hash IS
    'Hash da linha anterior; 64 zeros na primeira linha da cadeia.';

-- Cadeia linear: dois atos concorrentes não podem apontar para o mesmo
-- predecessor. A escrita também toma um advisory lock na transação.
CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_log_prev_hash
    ON audit_log (prev_hash);
CREATE UNIQUE INDEX IF NOT EXISTS uq_audit_log_hash
    ON audit_log (hash);

CREATE INDEX IF NOT EXISTS idx_audit_log_occurred
    ON audit_log (occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor
    ON audit_log (actor_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity
    ON audit_log (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action
    ON audit_log (action, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_trace
    ON audit_log (trace_id)
    WHERE trace_id IS NOT NULL;

CREATE OR REPLACE FUNCTION audit_log_reject_mutation()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_log é append-only: % não é permitido', TG_OP
        USING ERRCODE = '42501';
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_log_append_only ON audit_log;
CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION audit_log_reject_mutation();
