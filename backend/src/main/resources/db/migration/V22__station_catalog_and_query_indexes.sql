-- V22: catálogo único de estruturas e índices para lista paginada/filtrada.
--
-- Posto fixo e base móvel são cadastros distintos, mas o painel da coordenação
-- e o painel da TI mostram os dois na mesma lista, com o mesmo filtro (ativo,
-- tipo, nome) e a mesma paginação por cursor. A view junta as duas fontes uma
-- única vez e evita repetir o UNION ALL (e as subconsultas de fotos) em cada
-- consulta, mantendo as tabelas de origem como fonte da verdade.
--
-- A ordenação da página é `(kind, name, id)`; o par `name`/`id` é único e não
-- muda quando a estrutura é ativada/desativada, então o keyset não repete nem
-- pula linhas entre duas páginas.

CREATE VIEW station_catalog AS
SELECT p.id,
       'FIXED'::text AS kind,
       p.name,
       p.beach_id,
       b.name AS beach_name,
       p.location,
       p.position_source,
       p.position_accuracy_m,
       p.position_updated_at,
       p.active,
       p.deactivated_at,
       p.status_note,
       ARRAY(
           SELECT '/api/v1/media/' || pm.media_id
             FROM lifeguard_post_media pm
            WHERE pm.post_id = p.id
            ORDER BY pm.display_order, pm.media_id
       ) AS photo_urls
  FROM lifeguard_posts p
  JOIN beaches b ON b.id = p.beach_id
UNION ALL
SELECT m.id,
       'MOBILE'::text AS kind,
       m.name,
       NULL::uuid AS beach_id,
       NULL::text AS beach_name,
       m.location,
       m.position_source,
       m.position_accuracy_m,
       m.position_updated_at,
       m.active,
       m.deactivated_at,
       m.status_note,
       ARRAY(
           SELECT '/api/v1/media/' || mm.media_id
             FROM mobile_base_media mm
            WHERE mm.base_id = m.id
            ORDER BY mm.display_order, mm.media_id
       ) AS photo_urls
  FROM mobile_bases m;

-- Lista sem filtro de estado (ordem do cursor) e lista por estado.
CREATE INDEX IF NOT EXISTS idx_lifeguard_posts_catalog
    ON lifeguard_posts (name, id);
CREATE INDEX IF NOT EXISTS idx_lifeguard_posts_catalog_active
    ON lifeguard_posts (active, name, id);

CREATE INDEX IF NOT EXISTS idx_mobile_bases_catalog
    ON mobile_bases (name, id);
CREATE INDEX IF NOT EXISTS idx_mobile_bases_catalog_active
    ON mobile_bases (active, name, id);

-- Ocorrências: paginação por (occurred_at, number) em todo o escopo e filtro
-- por situação. O índice de CIOSP pendente já existe desde a V17.
CREATE INDEX IF NOT EXISTS idx_occ_occurred_number
    ON occurrences (occurred_at DESC, number DESC);
CREATE INDEX IF NOT EXISTS idx_occ_status_occurred_number
    ON occurrences (status, occurred_at DESC, number DESC);
