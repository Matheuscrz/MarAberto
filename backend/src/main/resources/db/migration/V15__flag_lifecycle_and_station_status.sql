-- V15: validade da sinalização e ciclo de vida das estruturas operacionais.
--
-- Nada é apagado. Bandeira vencida ou encerrada continua no histórico; posto
-- ou base desativado continua com as FKs de plantão e ocorrência. O que muda
-- é o critério de "vigente" aplicado na consulta pública:
--   * bandeira vigente  = closed_at IS NULL AND valid_until > now();
--   * posto ativo       = active;
--   * base móvel viva   = active + plantão aberto com last_position fresca.
-- Zonas críticas permanentes (foz de rio, costão) já nascem sem validade e
-- por isso não dependem desta migração.

ALTER TABLE lifeguard_posts
    ADD COLUMN deactivated_at TIMESTAMPTZ,
    ADD COLUMN status_note    VARCHAR(200);

ALTER TABLE mobile_bases
    ADD COLUMN deactivated_at TIMESTAMPTZ,
    ADD COLUMN status_note    VARCHAR(200);

-- Estrutura já inativa ganha a data aproximada, para o painel não mostrar
-- "desativada desde o Big Bang".
UPDATE lifeguard_posts
SET deactivated_at = updated_at
WHERE NOT active AND deactivated_at IS NULL;

UPDATE mobile_bases
SET deactivated_at = updated_at
WHERE NOT active AND deactivated_at IS NULL;

CREATE TABLE flag_updates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    beach_id    UUID NOT NULL REFERENCES beaches (id),
    -- Nulo = lançamento da praia inteira; preenchido = setor específico.
    sector_id   UUID REFERENCES beach_sectors (id),
    post_id     UUID REFERENCES lifeguard_posts (id),
    flag        VARCHAR(20) NOT NULL,
    notes       VARCHAR(400),
    set_by      UUID NOT NULL REFERENCES operational_users (id),
    valid_until TIMESTAMPTZ NOT NULL,
    -- Encerramento antecipado (nova bandeira no mesmo escopo ou "retirar
    -- sinalização"). A validade original permanece registrada.
    closed_at   TIMESTAMPTZ,
    closed_by   UUID REFERENCES operational_users (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_flag_updates_color
        CHECK (flag IN ('GREEN', 'YELLOW', 'RED', 'PURPLE', 'BLACK')),
    CONSTRAINT ck_flag_updates_validity
        CHECK (valid_until > created_at),
    CONSTRAINT ck_flag_updates_close
        CHECK (closed_at IS NULL OR closed_at >= created_at),
    CONSTRAINT ck_flag_updates_closed_by
        CHECK (closed_at IS NULL OR closed_by IS NOT NULL)
);

-- Consulta quente: bandeira vigente por praia/setor (mapa e ficha).
CREATE INDEX idx_flag_updates_open
    ON flag_updates (beach_id, sector_id, created_at DESC)
    WHERE closed_at IS NULL;

-- Varredura do que venceu (painel da coordenação e expurgo de evento).
CREATE INDEX idx_flag_updates_valid_until
    ON flag_updates (valid_until)
    WHERE closed_at IS NULL;
