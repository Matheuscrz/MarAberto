-- V17: ocorrência operacional (ADR-013).
--
-- A ocorrência é a ficha do atendimento; o SOS continua sendo o alerta em
-- tempo real. Ela nasce quando o guarda-vidas **assume** o chamado (ack): é
-- nesse instante que o atendimento passa a existir. Um SOS que ninguém assume
-- não vira ocorrência — o alerta, o escalonamento e o registro do chamado
-- continuam em `emergency_calls`.
--
-- `emergency_call_id UNIQUE` + `ON CONFLICT DO NOTHING` torna a criação
-- idempotente: o evento do ack é entregue mais de uma vez e o job de
-- reconciliação pode rodar sem duplicar. Nada é apagado: a ocorrência é
-- encerrada com desfecho (`closed_at`), como manda a RN11.

CREATE TABLE occurrences (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    number              BIGINT GENERATED ALWAYS AS IDENTITY,           -- nº interno legível
    client_request_id   UUID UNIQUE,                                   -- idempotência do registro manual
    origin              VARCHAR(20) NOT NULL,                          -- SOS_CITIZEN, CBM_193, EXTERNAL_OTHER, FIELD_ATTENDED
    emergency_call_id   UUID UNIQUE REFERENCES emergency_calls (id),   -- 1:1 quando origin = SOS_CITIZEN
    external_protocol   VARCHAR(40),                                   -- nº do 193 ou de outro canal (opcional)
    nature              VARCHAR(30) NOT NULL,                          -- RESCUE, FIRST_AID, DROWNING, MISSING_PERSON, MARINE_ANIMAL, OTHER
    status              VARCHAR(20) NOT NULL,                          -- OPEN, IN_PROGRESS, CLOSED, CANCELLED, FALSE_ALARM
    occurred_at         TIMESTAMPTZ NOT NULL,                          -- quando o FATO ocorreu (≠ created_at)
    location            GEOGRAPHY(Point, 4326),                        -- sensível: só em rota autenticada
    location_accuracy_m NUMERIC(7, 1),
    beach_id            UUID REFERENCES beaches (id),
    beach_sector_id     UUID REFERENCES beach_sectors (id),            -- setor operacional (ADR-016)
    reference_text      TEXT,                                          -- ponto de referência (sanitizado)
    post_id             UUID REFERENCES lifeguard_posts (id),
    summary             TEXT,                                          -- cifrado na aplicação (ADR-012)
    outcome_type        VARCHAR(30),                                   -- preenchido ao encerrar (RF28)
    outcome_notes       TEXT,                                          -- cifrado na aplicação
    ciosp_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',        -- PENDING, REGISTERED, NOT_REQUIRED
    ciosp_protocol      VARCHAR(40),
    ciosp_registered_at TIMESTAMPTZ,
    ciosp_registered_by UUID REFERENCES operational_users (id),
    registered_by       UUID REFERENCES operational_users (id),        -- nulo quando criada pelo sistema (SOS)
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMPTZ,
    CONSTRAINT chk_occ_origin CHECK (
        origin IN ('SOS_CITIZEN', 'CBM_193', 'EXTERNAL_OTHER', 'FIELD_ATTENDED')),
    CONSTRAINT chk_occ_nature CHECK (
        nature IN ('RESCUE', 'FIRST_AID', 'DROWNING', 'MISSING_PERSON', 'MARINE_ANIMAL', 'OTHER')),
    CONSTRAINT chk_occ_status CHECK (
        status IN ('OPEN', 'IN_PROGRESS', 'CLOSED', 'CANCELLED', 'FALSE_ALARM')),
    CONSTRAINT chk_occ_ciosp_status CHECK (
        ciosp_status IN ('PENDING', 'REGISTERED', 'NOT_REQUIRED')),
    CONSTRAINT chk_occ_outcome CHECK (
        outcome_type IS NULL OR outcome_type IN ('RESOLVED', 'FALSE_ALARM', 'CANCELLED')),
    CONSTRAINT chk_occ_sos_link CHECK (origin <> 'SOS_CITIZEN' OR emergency_call_id IS NOT NULL),
    -- RN12: sem coordenada, é obrigatório praia + ponto de referência.
    CONSTRAINT chk_occ_place CHECK (
        location IS NOT NULL OR (beach_id IS NOT NULL AND reference_text IS NOT NULL)),
    -- Ocorrência de SOS é criada pelo sistema; as demais têm quem registrou.
    CONSTRAINT chk_occ_registered_by CHECK (origin <> 'SOS_CITIZEN' OR registered_by IS NULL)
);

CREATE INDEX idx_occ_loc     ON occurrences USING GIST (location);
CREATE INDEX idx_occ_pending ON occurrences (occurred_at DESC) WHERE ciosp_status = 'PENDING';
CREATE INDEX idx_occ_post    ON occurrences (post_id, occurred_at DESC);
CREATE INDEX idx_occ_beach   ON occurrences (beach_id, occurred_at DESC);

-- Histórico append-only: abrir, mudar de estado, editar, vincular, registrar
-- no CIOSP. `detail` nunca contém PII em claro.
CREATE TABLE occurrence_events (
    id            BIGSERIAL PRIMARY KEY,
    occurrence_id UUID NOT NULL REFERENCES occurrences (id),
    kind          VARCHAR(30) NOT NULL,
    actor_id      UUID REFERENCES operational_users (id),
    detail        JSONB,
    at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_occ_events_kind CHECK (
        kind IN ('OPENED', 'STATUS_CHANGED', 'EDITED', 'LINKED', 'CIOSP_REGISTERED', 'PERSON_REVEALED'))
);

CREATE INDEX idx_occ_events_occurrence ON occurrence_events (occurrence_id, at);
