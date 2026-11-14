-- V19: foto das estruturas operacionais (posto fixo e base móvel).
--
-- O guarda-vidas cadastra o posto/base na posição atual e pode anexar até três
-- fotos. A população vê a estrutura no mapa com a foto do posto, do mesmo jeito
-- que já vê a foto da sinalização: o upload passa pelo backend, é
-- re-codificado (EXIF/GPS fora) e servido por `GET /api/v1/media/{id}`.
--
-- As fotos são da estrutura, não do operador: a tabela de ligação é própria e
-- o vínculo aponta para `media_assets` (que guarda dono e conteúdo). Duas
-- tabelas porque `lifeguard_posts` e `mobile_bases` são cadastros distintos,
-- com históricos e FKs próprias — a integridade referencial fica no banco.

CREATE TABLE lifeguard_post_media (
    post_id       UUID NOT NULL REFERENCES lifeguard_posts (id) ON DELETE CASCADE,
    media_id      UUID NOT NULL REFERENCES media_assets (id),
    display_order SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id, media_id)
);

CREATE INDEX idx_lifeguard_post_media_media ON lifeguard_post_media (media_id);

CREATE TABLE mobile_base_media (
    base_id       UUID NOT NULL REFERENCES mobile_bases (id) ON DELETE CASCADE,
    media_id      UUID NOT NULL REFERENCES media_assets (id),
    display_order SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (base_id, media_id)
);

CREATE INDEX idx_mobile_base_media_media ON mobile_base_media (media_id);
