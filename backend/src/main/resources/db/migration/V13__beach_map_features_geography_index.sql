-- Consulta pública por proximidade (RF01): a lista de praias mostra apenas o
-- que está num raio de até 30 km de quem consulta.
--
-- O mapa é projetado em `beach_map_features` com geometria em 4326; a distância
-- é medida em metros sobre `geography` para não depender de conversão de graus.
-- Sem este índice de expressão o PostGIS recalcularia a projeção de toda a
-- view em cada consulta.

CREATE INDEX idx_beach_map_features_geog
    ON beach_map_features USING GIST ((geometry::geography));

COMMENT ON INDEX idx_beach_map_features_geog IS
    'Filtro de distância (ST_DWithin) da lista pública de praias';
