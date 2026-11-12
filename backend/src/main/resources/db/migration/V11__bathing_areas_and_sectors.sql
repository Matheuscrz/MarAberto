-- Área de banho (praia, rio, barragem), setores operacionais e proveniência
-- da importação QGIS/KML (ADR-016 + ADR-021).

ALTER TABLE beaches
    ADD COLUMN kind            VARCHAR(20) NOT NULL DEFAULT 'BEACH',
    ADD COLUMN external_id     VARCHAR(160),
    ADD COLUMN source          VARCHAR(80),
    ADD COLUMN source_version  VARCHAR(64),
    ADD COLUMN geometry_hash   VARCHAR(64),
    ADD COLUMN source_metadata JSONB,
    ADD COLUMN imported_at     TIMESTAMPTZ,
    ADD COLUMN imported_by     UUID REFERENCES operational_users (id),
    ADD COLUMN active          BOOLEAN NOT NULL DEFAULT TRUE;

-- Uma feição do KML pode ter mais de um polígono.
ALTER TABLE beaches
    ALTER COLUMN boundary TYPE geometry(MultiPolygon, 4326)
    USING ST_Multi(boundary);

ALTER TABLE beaches
    ADD CONSTRAINT uq_beach_external UNIQUE (external_id),
    ADD CONSTRAINT ck_beach_kind CHECK (kind IN ('BEACH', 'RIVER', 'DAM', 'LAGOON', 'OTHER'));

CREATE INDEX idx_beaches_active ON beaches (active) WHERE active;

-- Cada Placemark do KML é um setor. Nome é rótulo, não identidade: pode ser
-- nulo (setor sem nome) e pode repetir dentro da mesma área.
CREATE TABLE beach_sectors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    beach_id        UUID NOT NULL REFERENCES beaches (id) ON DELETE CASCADE,
    external_id     VARCHAR(160) NOT NULL,
    name            TEXT,
    display_order   INT NOT NULL DEFAULT 0,
    geometry        geometry(MultiPolygon, 4326) NOT NULL,
    geometry_hash   VARCHAR(64) NOT NULL,
    source          VARCHAR(80),
    source_version  VARCHAR(64),
    source_metadata JSONB,
    imported_at     TIMESTAMPTZ,
    imported_by     UUID REFERENCES operational_users (id),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sector_external UNIQUE (external_id),
    CONSTRAINT chk_sector_geom CHECK (ST_IsValid(geometry))
);

CREATE INDEX idx_sector_beach ON beach_sectors (beach_id);
CREATE INDEX idx_sector_geom ON beach_sectors USING GIST (geometry);
CREATE INDEX idx_sector_active ON beach_sectors (beach_id) WHERE active;

-- Auditoria de cada execução de importação (inclusive dry-run).
CREATE TABLE import_batches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_file     VARCHAR(255) NOT NULL,
    source_version  VARCHAR(64) NOT NULL,
    state_code      VARCHAR(2) NOT NULL,
    mode            VARCHAR(20) NOT NULL,
    dry_run         BOOLEAN NOT NULL,
    applied         BOOLEAN NOT NULL,
    created_count   INT NOT NULL DEFAULT 0,
    updated_count   INT NOT NULL DEFAULT 0,
    unchanged_count INT NOT NULL DEFAULT 0,
    missing_count   INT NOT NULL DEFAULT 0,
    rejected_count  INT NOT NULL DEFAULT 0,
    imported_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    imported_by     UUID REFERENCES operational_users (id),
    report          JSONB NOT NULL,
    CONSTRAINT ck_import_mode CHECK (mode IN ('UPSERT', 'SYNC'))
);

CREATE INDEX idx_import_batches_state ON import_batches (state_code, imported_at DESC);

-- `updated_at` é responsabilidade do banco: vale para JPA, importação e SQL manual.
CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_beaches_touch
    BEFORE UPDATE ON beaches
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_beach_sectors_touch
    BEFORE UPDATE ON beach_sectors
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- Projeção de leitura do mapa público (áreas + setores). Evita juntar e
-- desserializar a geometria a cada request; o importador atualiza a view uma
-- vez por lote, fora da transação, e eventuais escritas futuras seguem o mesmo
-- caminho. `active` fica na view para o consumidor filtrar sem perder o cache.
CREATE MATERIALIZED VIEW beach_map_features AS
SELECT 'AREA'::text   AS feature_kind,
       b.id           AS feature_id,
       b.id           AS beach_id,
       NULL::uuid     AS sector_id,
       b.name         AS name,
       b.kind         AS kind,
       b.municipality AS municipality,
       b.state_code   AS state_code,
       b.boundary     AS geometry,
       b.active       AS active
  FROM beaches b
UNION ALL
SELECT 'SECTOR'::text,
       s.id,
       s.beach_id,
       s.id,
       s.name,
       b.kind,
       b.municipality,
       b.state_code,
       s.geometry,
       s.active
  FROM beach_sectors s
  JOIN beaches b ON b.id = s.beach_id;

CREATE UNIQUE INDEX uq_beach_map_features ON beach_map_features (feature_kind, feature_id);
CREATE INDEX idx_beach_map_features_geom ON beach_map_features USING GIST (geometry);
CREATE INDEX idx_beach_map_features_active ON beach_map_features (state_code, feature_kind) WHERE active;

REFRESH MATERIALIZED VIEW beach_map_features;
