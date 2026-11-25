-- Conteúdos educativos, vídeos (youtube-nocookie) e materiais de divulgação (Fatia 12).

CREATE TABLE IF NOT EXISTS educational_content (
    id                  UUID PRIMARY KEY,
    kind                VARCHAR(20) NOT NULL CHECK (kind IN ('VIDEO', 'GUIDE', 'CAMPAIGN')),
    title               VARCHAR(160) NOT NULL,
    description         TEXT NOT NULL,
    category            VARCHAR(60) NOT NULL,
    tags                TEXT[] NOT NULL DEFAULT '{}',
    external_url        VARCHAR(500),
    external_link_label VARCHAR(80),
    youtube_id          VARCHAR(11),
    duration            VARCHAR(16),
    display_order       INTEGER NOT NULL DEFAULT 0,
    published           BOOLEAN NOT NULL DEFAULT FALSE,
    created_by          UUID,
    updated_by          UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_educational_content_public
    ON educational_content (display_order ASC, created_at DESC)
    WHERE published = TRUE;

CREATE INDEX IF NOT EXISTS idx_educational_content_order
    ON educational_content (display_order ASC, created_at DESC);
