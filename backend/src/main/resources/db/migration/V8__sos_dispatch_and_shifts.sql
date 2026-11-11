-- V8: despacho do SOS por destinatário, plantões e geoindexação do chamado.
--
-- Duas responsabilidades separadas (ADR-018): o PostGIS decide quem deve ser
-- notificado, materializado em emergency_dispatches; o transporte apenas
-- entrega. Sem plantão aberto não há como saber "quem está perto", por isso a
-- tabela shift_sessions entra nesta migração.

-- Raio de cobertura por posto/base (metros). Nulo usa o padrão da instalação.
ALTER TABLE lifeguard_posts
    ADD COLUMN coverage_radius_m INTEGER;
ALTER TABLE lifeguard_posts
    ADD CONSTRAINT ck_lifeguard_posts_coverage
    CHECK (coverage_radius_m IS NULL OR coverage_radius_m BETWEEN 100 AND 50000);

ALTER TABLE mobile_bases
    ADD COLUMN coverage_radius_m INTEGER;
ALTER TABLE mobile_bases
    ADD CONSTRAINT ck_mobile_bases_coverage
    CHECK (coverage_radius_m IS NULL OR coverage_radius_m BETWEEN 100 AND 50000);

-- Plantão: um operador em um posto fixo OU em uma base móvel.
CREATE TABLE shift_sessions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES operational_users (id) ON DELETE CASCADE,
    post_id                 UUID REFERENCES lifeguard_posts (id),
    mobile_base_id          UUID REFERENCES mobile_bases (id),
    beach_id                UUID REFERENCES beaches (id),
    started_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at                TIMESTAMPTZ,
    -- Última posição conhecida do plantão (base móvel). Só é usada para
    -- elegibilidade e para o painel do escopo autorizado (RN17).
    last_position           GEOGRAPHY(Point, 4326),
    last_position_at        TIMESTAMPTZ,
    last_position_accuracy_m DOUBLE PRECISION,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_shift_target CHECK (num_nonnulls(post_id, mobile_base_id) = 1),
    CONSTRAINT ck_shift_end CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT ck_shift_accuracy CHECK (
        last_position_accuracy_m IS NULL OR last_position_accuracy_m > 0)
);

-- Um plantão aberto por operador: evita que a mesma pessoa receba o SOS duas
-- vezes e dá ao ack um único posto responsável.
CREATE UNIQUE INDEX uq_shift_open ON shift_sessions (user_id) WHERE ended_at IS NULL;
CREATE INDEX idx_shift_post_open ON shift_sessions (post_id) WHERE ended_at IS NULL;
CREATE INDEX idx_shift_base_open ON shift_sessions (mobile_base_id) WHERE ended_at IS NULL;
CREATE INDEX idx_shift_beach_open ON shift_sessions (beach_id) WHERE ended_at IS NULL;

-- Coluna geográfica derivada do chamado (ADR-017): permite ST_DWithin/KNN e
-- desenho no painel sem recalcular o ponto a cada leitura.
ALTER TABLE emergency_calls
    ADD COLUMN location GEOGRAPHY(Point, 4326)
    GENERATED ALWAYS AS (
        CAST(ST_SetSRID(ST_MakePoint(longitude, latitude), 4326) AS geography)
    ) STORED;
CREATE INDEX idx_emergency_calls_location ON emergency_calls USING GIST (location);

-- Ciclo de vida do chamado usado pelo ack por compare-and-set.
ALTER TABLE emergency_calls
    ADD COLUMN assigned_post_id UUID REFERENCES lifeguard_posts (id),
    ADD COLUMN acknowledged_at  TIMESTAMPTZ,
    ADD COLUMN version          BIGINT NOT NULL DEFAULT 0;

-- Despacho passa a ser a prova de notificação: uma linha por destinatário,
-- com rodada, distância e horários de notificação/ack/cancelamento.
ALTER TABLE emergency_dispatches
    ADD COLUMN round             SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN recipient_post_id UUID REFERENCES lifeguard_posts (id),
    ADD COLUMN recipient_base_id UUID REFERENCES mobile_bases (id),
    ADD COLUMN recipient_user_id UUID REFERENCES operational_users (id),
    ADD COLUMN distance_m        NUMERIC(8, 1),
    ADD COLUMN position_stale    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN notified_at       TIMESTAMPTZ,
    ADD COLUMN acknowledged_at   TIMESTAMPTZ,
    ADD COLUMN cancelled_at      TIMESTAMPTZ;

-- O despacho antigo (sem destinatário) é dado de desenvolvimento. A V3 é
-- imutável; a constraint entra como NOT VALID para não quebrar linhas legadas,
-- mas vale para toda linha nova.
ALTER TABLE emergency_dispatches DROP COLUMN assigned_post_id;
ALTER TABLE emergency_dispatches
    ADD CONSTRAINT ck_emergency_dispatches_recipient
    CHECK (num_nonnulls(recipient_post_id, recipient_base_id, recipient_user_id) >= 1)
    NOT VALID;
ALTER TABLE emergency_dispatches
    ADD CONSTRAINT ck_emergency_dispatches_distance
    CHECK (distance_m IS NULL OR distance_m >= 0);

CREATE UNIQUE INDEX uq_emergency_dispatches_recipient
    ON emergency_dispatches (
        emergency_call_id,
        round,
        COALESCE(recipient_post_id, recipient_base_id, recipient_user_id));
CREATE INDEX idx_emergency_dispatches_pending
    ON emergency_dispatches (created_at) WHERE status = 'PENDING';
CREATE INDEX idx_emergency_dispatches_unacked
    ON emergency_dispatches (created_at) WHERE status IN ('PENDING', 'NOTIFIED');
CREATE INDEX idx_emergency_dispatches_recipient_user
    ON emergency_dispatches (recipient_user_id, status);

-- Reivindicação da outbox com prazo (lease): a publicação acontece fora da
-- transação que bloqueia as linhas e outra instância pode assumir depois.
ALTER TABLE emergency_outbox_events ADD COLUMN claimed_at TIMESTAMPTZ;
