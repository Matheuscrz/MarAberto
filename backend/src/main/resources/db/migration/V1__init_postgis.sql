-- Habilita o suporte geoespacial do PostgreSQL.
CREATE EXTENSION IF NOT EXISTS postgis;

-- Praias: polígono oficial da faixa de areia/área de banho.
CREATE TABLE beaches (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(120) NOT NULL,
    municipality VARCHAR(120),
    state_code   VARCHAR(2) NOT NULL,
    boundary     GEOMETRY(Polygon, 4326) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_beaches_boundary ON beaches USING GIST (boundary);

-- Postos fixos de guarda-vidas: ponto geográfico para cálculos em metros.
CREATE TABLE lifeguard_posts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    beach_id   UUID NOT NULL REFERENCES beaches (id) ON DELETE CASCADE,
    name       VARCHAR(120) NOT NULL,
    location   GEOGRAPHY(Point, 4326) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lifeguard_posts_beach ON lifeguard_posts (beach_id);
CREATE INDEX idx_lifeguard_posts_location ON lifeguard_posts USING GIST (location);

-- Bases móveis: localização corrente informada durante o plantão.
CREATE TABLE mobile_bases (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(120) NOT NULL,
    location   GEOGRAPHY(Point, 4326) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mobile_bases_location ON mobile_bases USING GIST (location);

-- Zonas críticas: perigos estruturais permanentes (corrente de retorno,
-- foz de rio, costões etc.) com aviso mantido no mapa.
CREATE TABLE critical_zones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    beach_id    UUID NOT NULL REFERENCES beaches (id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    risk_type   VARCHAR(40) NOT NULL,
    description TEXT,
    geometry    GEOMETRY(Polygon, 4326) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_critical_zones_beach ON critical_zones (beach_id);
CREATE INDEX idx_critical_zones_geometry ON critical_zones USING GIST (geometry);
