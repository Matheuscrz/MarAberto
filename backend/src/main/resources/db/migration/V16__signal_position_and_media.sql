-- V16: sinalização ancorada no aparelho e mídia (foto) da sinalização.
--
-- Duas formas de lançar a bandeira passam a conviver:
--   1. por escopo escolhido no console (praia inteira ou setor) — `sector_id`;
--   2. pelo ponto atual do guarda-vidas — `signal_position`, com praia/setor
--      resolvidos pelo servidor a partir da coordenada.
-- O ponto é da sinalização (a bandeira física plantada ali), não um histórico
-- de deslocamento: cada lançamento guarda um ponto só, e o mapa público mostra
-- apenas a bandeira vigente. A foto é opcional e chega pela tabela de mídia.
--
-- Nada é apagado: encerrar ou vencer continua sendo `closed_at`/`valid_until`.

CREATE TABLE media_assets (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    object_key   VARCHAR(255) NOT NULL,
    content_type VARCHAR(60) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    width_px     INTEGER,
    height_px    INTEGER,
    -- Conteúdo normalizado (EXIF removido), guardado para deduplicação auditável.
    sha256       BYTEA NOT NULL,
    owner_id     UUID NOT NULL REFERENCES operational_users (id),
    status       VARCHAR(20) NOT NULL DEFAULT 'READY',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_media_assets_status CHECK (status IN ('PENDING', 'READY', 'REJECTED')),
    CONSTRAINT chk_media_assets_size CHECK (size_bytes > 0),
    CONSTRAINT chk_media_assets_dimensions CHECK (
        (width_px IS NULL AND height_px IS NULL)
        OR (width_px > 0 AND height_px > 0))
);

CREATE UNIQUE INDEX uq_media_assets_object_key ON media_assets (object_key);
CREATE INDEX idx_media_assets_owner ON media_assets (owner_id, created_at DESC);

-- Uma sinalização pode ter mais de uma foto (a bandeira e o mar, por exemplo).
CREATE TABLE flag_media (
    flag_id       UUID NOT NULL REFERENCES flag_updates (id) ON DELETE CASCADE,
    media_id      UUID NOT NULL REFERENCES media_assets (id),
    display_order SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (flag_id, media_id)
);

CREATE INDEX idx_flag_media_media ON flag_media (media_id);

ALTER TABLE flag_updates
    -- Ponto enviado pelo aparelho no lançamento por localização; nulo quando o
    -- escopo foi escolhido no console.
    ADD COLUMN signal_position  GEOGRAPHY(Point, 4326),
    ADD COLUMN position_accuracy_m NUMERIC(7, 1),
    ADD COLUMN position_source  VARCHAR(20);

ALTER TABLE flag_updates
    ADD CONSTRAINT chk_flag_updates_position_source
        CHECK (position_source IS NULL OR position_source IN ('DEVICE_GPS', 'SCOPE'));

CREATE INDEX idx_flag_updates_position ON flag_updates USING GIST (signal_position);

-- A posição oficial da estrutura também pode vir do aparelho (RF11): o painel
-- guarda a origem e a acurácia para a coordenação julgar o ponto.
ALTER TABLE lifeguard_posts
    ADD COLUMN position_source     VARCHAR(20),
    ADD COLUMN position_accuracy_m NUMERIC(7, 1),
    ADD COLUMN position_updated_at TIMESTAMPTZ;

ALTER TABLE mobile_bases
    ADD COLUMN position_source     VARCHAR(20),
    ADD COLUMN position_accuracy_m NUMERIC(7, 1),
    ADD COLUMN position_updated_at TIMESTAMPTZ;

ALTER TABLE lifeguard_posts
    ADD CONSTRAINT chk_lifeguard_posts_position_source
        CHECK (position_source IS NULL OR position_source IN ('MANUAL', 'DEVICE_GPS', 'MAP_PICK'));

ALTER TABLE mobile_bases
    ADD CONSTRAINT chk_mobile_bases_position_source
        CHECK (position_source IS NULL OR position_source IN ('MANUAL', 'DEVICE_GPS', 'MAP_PICK'));

-- Estrutura já cadastrada não tem origem registrada: assume cadastro manual e a
-- data do último toque, para o painel não mostrar "posição desconhecida".
UPDATE lifeguard_posts
SET position_source = 'MANUAL',
    position_updated_at = updated_at
WHERE position_source IS NULL;

UPDATE mobile_bases
SET position_source = 'MANUAL',
    position_updated_at = updated_at
WHERE position_source IS NULL;
