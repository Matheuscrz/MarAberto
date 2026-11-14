-- V18: sinalização registrada em área não identificada.
--
-- O guarda-vidas pode estar num ponto que ainda não está no mapeamento do CBM:
-- uma praia nova, um trecho de rio que ninguém cadastrou, uma barragem que
-- passou a receber banhistas. Recusar o lançamento apagaria a informação de
-- campo; o registro entra na fila de triagem da coordenação, que decide entre
-- associar a uma área existente (`IDENTIFIED`), pedir um novo mapeamento
-- (`NEEDS_MAPPING`) ou descartar (`DISCARDED`).
--
-- **Nada é publicado no mapa**: `flag_updates` não recebe linha e a leitura
-- pública (`GET /api/v1/flags`) não toca nesta tabela. A coordenada, as fotos e
-- a matrícula de quem reportou só são lidas em rota autenticada da coordenação
-- (RN17), e o ponto guardado é o do lançamento — não um histórico de trajeto.
--
-- A linha nunca é apagada: a decisão é um estado (`status`) com autor e data.

CREATE TABLE unmapped_signals (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Cor que o operador queria lançar; fica registrada para a triagem saber o
    -- que ele viu, mesmo sem uma bandeira publicada.
    flag                VARCHAR(20) NOT NULL,
    notes               VARCHAR(400),
    location            GEOGRAPHY(Point, 4326) NOT NULL,
    location_accuracy_m NUMERIC(7, 1),
    reported_by         UUID NOT NULL REFERENCES operational_users (id),
    reported_post_id    UUID REFERENCES lifeguard_posts (id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- Preenchidos só quando a coordenação identifica a área/setor existente.
    beach_id            UUID REFERENCES beaches (id),
    sector_id           UUID REFERENCES beach_sectors (id),
    decision_notes      VARCHAR(500),
    decided_by          UUID REFERENCES operational_users (id),
    decided_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_unmapped_signals_color CHECK (
        flag IN ('GREEN', 'YELLOW', 'RED', 'PURPLE', 'BLACK')),
    CONSTRAINT ck_unmapped_signals_status CHECK (
        status IN ('PENDING', 'IDENTIFIED', 'NEEDS_MAPPING', 'DISCARDED')),
    -- Identificar exige a área; o setor é opcional (praia inteira).
    CONSTRAINT ck_unmapped_signals_identified CHECK (
        status <> 'IDENTIFIED' OR beach_id IS NOT NULL),
    -- Toda decisão registra quem decidiu e quando; reabrir limpa os dois.
    CONSTRAINT ck_unmapped_signals_decided CHECK (
        status = 'PENDING' OR (decided_by IS NOT NULL AND decided_at IS NOT NULL))
);

-- Fila da coordenação: pendentes primeiro, das mais recentes para as antigas.
CREATE INDEX idx_unmapped_signals_queue
    ON unmapped_signals (status, created_at DESC);

CREATE INDEX idx_unmapped_signals_location
    ON unmapped_signals USING GIST (location);

-- Fotos do registro, na ordem em que foram enviadas (até 3, como na bandeira).
CREATE TABLE unmapped_signal_media (
    signal_id     UUID NOT NULL REFERENCES unmapped_signals (id) ON DELETE CASCADE,
    media_id      UUID NOT NULL REFERENCES media_assets (id),
    display_order SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (signal_id, media_id)
);

CREATE INDEX idx_unmapped_signal_media_media ON unmapped_signal_media (media_id);
