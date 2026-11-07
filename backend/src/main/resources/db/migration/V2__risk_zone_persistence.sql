-- Persistência temporal das zonas críticas.
-- Uma corrente de retorno pode ser estrutural (permanente), topográfica
-- (semipermanente) ou migratória (efêmera). Esta migração cobre as duas
-- primeiras; correntes efêmeras pertencem a rip_current_markings (operations).
ALTER TABLE critical_zones
    ADD COLUMN persistence       VARCHAR(20) NOT NULL DEFAULT 'PERMANENT',
    ADD COLUMN valid_from        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN valid_until       TIMESTAMPTZ,
    ADD COLUMN last_confirmed_at TIMESTAMPTZ;

ALTER TABLE critical_zones
    ADD CONSTRAINT ck_critical_zones_persistence
        CHECK (persistence IN ('PERMANENT', 'SEMI_PERMANENT')),
    ADD CONSTRAINT ck_critical_zones_validity
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    ADD CONSTRAINT ck_critical_zones_permanent_no_expiry
        CHECK (persistence <> 'PERMANENT' OR valid_until IS NULL),
    ADD CONSTRAINT ck_critical_zones_semi_permanent_expiry
        CHECK (persistence <> 'SEMI_PERMANENT' OR valid_until IS NOT NULL);

-- Apoia a varredura de zonas vencidas (desativação/expurgo).
CREATE INDEX idx_critical_zones_valid_until ON critical_zones (valid_until)
    WHERE active AND valid_until IS NOT NULL;
