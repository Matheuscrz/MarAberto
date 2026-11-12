package com.maraberto.beaches.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.beaches.api.BeachDetail;
import com.maraberto.beaches.api.BeachMap;
import com.maraberto.beaches.api.BeachMapBounds;
import com.maraberto.beaches.api.BeachMapSector;
import com.maraberto.beaches.api.BeachPosition;
import com.maraberto.beaches.api.BeachSectorSummary;
import com.maraberto.beaches.api.BeachSummary;
import com.maraberto.beaches.domain.BeachKind;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Leitura pública do mapa de áreas de banho.
 *
 * <p>Tudo sai da projeção materializada {@code beach_map_features}, que o
 * importador atualiza uma vez por lote: a lista e a ficha não pagam o custo de
 * juntar e desserializar a geometria a cada request. O filtro de distância é
 * feito em metros sobre {@code geography}, com o índice GiST da projeção.
 */
@Repository
public class PublicBeachRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PublicBeachRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Áreas ativas dentro do raio, da mais próxima para a mais distante.
     *
     * <p>A distância é medida até o polígono, não até o centro: quem está
     * dentro da área de banho vê {@code 0} m.
     */
    public List<BeachSummary> findNearby(double latitude, double longitude, int radiusMeters) {
        return jdbc.query("""
                        SELECT f.feature_id AS id,
                               f.name,
                               f.kind,
                               f.municipality,
                               f.state_code,
                               ST_Y(ST_Centroid(f.geometry)) AS latitude,
                               ST_X(ST_Centroid(f.geometry)) AS longitude,
                               ST_Distance(f.geometry::geography, p.location) AS distance_m,
                               (SELECT count(*)
                                  FROM beach_sectors s
                                 WHERE s.beach_id = f.feature_id
                                   AND s.active) AS sector_count
                          FROM beach_map_features f
                          CROSS JOIN (
                              SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography AS location
                          ) p
                         WHERE f.feature_kind = 'AREA'
                           AND f.active
                           AND ST_DWithin(f.geometry::geography, p.location, ?)
                         ORDER BY distance_m, f.name
                         LIMIT 200
                        """,
                (rs, rowNum) -> new BeachSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        BeachKind.valueOf(rs.getString("kind")),
                        rs.getString("municipality"),
                        rs.getString("state_code"),
                        rs.getInt("sector_count"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        Math.round(rs.getDouble("distance_m"))),
                longitude, latitude, radiusMeters);
    }

    /**
     * Área e setor que contêm o ponto, se ele estiver em uma área mapeada.
     *
     * <p>Setores de uma mesma área podem se sobrepor no mapeamento; nesse caso
     * vence o primeiro na ordem do KML. É a mesma regra usada pela amostra de
     * ocupação, para "onde estou" e "onde registro presença" nunca divergirem.
     */
    public Optional<BeachPosition> findPositionAt(double latitude, double longitude) {
        return jdbc.query("""
                        SELECT s.beach_id,
                               b.name AS beach_name,
                               s.id AS sector_id,
                               s.name AS sector_name,
                               s.display_order
                          FROM beach_sectors s
                          JOIN beaches b ON b.id = s.beach_id
                         WHERE s.active
                           AND b.active
                           AND ST_Contains(s.geometry, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                         ORDER BY s.display_order, s.id
                         LIMIT 1
                        """,
                (rs, rowNum) -> new BeachPosition(
                        rs.getObject("beach_id", UUID.class),
                        rs.getString("beach_name"),
                        rs.getObject("sector_id", UUID.class),
                        sectorLabel(rs.getString("sector_name"), rs.getInt("display_order"))),
                longitude, latitude).stream().findFirst();
    }

    public Optional<BeachDetail> findDetail(UUID beachId) {
        return jdbc.query("""
                        SELECT feature_id AS id,
                               name,
                               kind,
                               municipality,
                               state_code,
                               ST_Y(ST_Centroid(geometry)) AS latitude,
                               ST_X(ST_Centroid(geometry)) AS longitude
                           FROM beach_map_features
                          WHERE feature_kind = 'AREA'
                            AND feature_id = ?
                        """,
                (rs, rowNum) -> new BeachDetail(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        BeachKind.valueOf(rs.getString("kind")),
                        rs.getString("municipality"),
                        rs.getString("state_code"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        findSectors(rs.getObject("id", UUID.class))),
                beachId).stream().findFirst();
    }

    /**
     * Geometria da área e dos setores, simplificada para o zoom de bairro.
     *
     * @param tolerance tolerância em graus de {@code ST_SimplifyPreserveTopology}
     */
    public Optional<BeachMap> findMap(UUID beachId, double tolerance) {
        return jdbc.query("""
                        SELECT f.feature_id AS id,
                               f.name,
                               f.kind,
                               ST_XMin(ST_Envelope(f.geometry)) AS min_lon,
                               ST_YMin(ST_Envelope(f.geometry)) AS min_lat,
                               ST_XMax(ST_Envelope(f.geometry)) AS max_lon,
                               ST_YMax(ST_Envelope(f.geometry)) AS max_lat,
                               ST_AsGeoJSON(
                                   ST_SimplifyPreserveTopology(f.geometry, ?), 6) AS geojson
                          FROM beach_map_features f
                         WHERE f.feature_kind = 'AREA'
                           AND f.feature_id = ?
                        """,
                (rs, rowNum) -> new BeachMap(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        BeachKind.valueOf(rs.getString("kind")),
                        bounds(rs),
                        geojson(rs.getString("geojson")),
                        findMapSectors(rs.getObject("id", UUID.class), tolerance)),
                tolerance, beachId).stream().findFirst();
    }

    /**
     * Versão corrente do mapa: quantidade de lotes aplicados e instante do
     * último. {@code import_batches.id} é UUID, então não serve de contador; o
     * par (quantidade, último instante) muda a cada lote aplicado, que é
     * exatamente quando a projeção é recarregada.
     */
    public String mapVersion() {
        return jdbc.queryForObject("""
                        SELECT count(*) || '-' ||
                               coalesce(extract(epoch FROM max(imported_at))::bigint, 0)
                          FROM import_batches
                         WHERE applied
                        """,
                String.class);
    }

    private List<BeachMapSector> findMapSectors(UUID beachId, double tolerance) {
        return jdbc.query("""
                        SELECT id,
                               name,
                               display_order,
                               ST_XMin(ST_Envelope(geometry)) AS min_lon,
                               ST_YMin(ST_Envelope(geometry)) AS min_lat,
                               ST_XMax(ST_Envelope(geometry)) AS max_lon,
                               ST_YMax(ST_Envelope(geometry)) AS max_lat,
                               ST_AsGeoJSON(
                                   ST_SimplifyPreserveTopology(geometry, ?), 6) AS geojson
                          FROM beach_sectors
                         WHERE beach_id = ?
                           AND active
                         ORDER BY display_order, name NULLS LAST
                        """,
                (rs, rowNum) -> new BeachMapSector(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getInt("display_order"),
                        bounds(rs),
                        geojson(rs.getString("geojson"))),
                tolerance, beachId);
    }

    private List<BeachSectorSummary> findSectors(UUID beachId) {
        return jdbc.query("""
                        SELECT id,
                               name,
                               display_order,
                               ST_Y(ST_Centroid(geometry)) AS latitude,
                               ST_X(ST_Centroid(geometry)) AS longitude
                          FROM beach_sectors
                         WHERE beach_id = ?
                           AND active
                         ORDER BY display_order, name NULLS LAST
                        """,
                (rs, rowNum) -> new BeachSectorSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getInt("display_order"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude")),
                beachId);
    }

    private static BeachMapBounds bounds(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BeachMapBounds(
                rs.getDouble("min_lon"),
                rs.getDouble("min_lat"),
                rs.getDouble("max_lon"),
                rs.getDouble("max_lat"));
    }

    private JsonNode geojson(String json) {
        try {
            return objectMapper.readTree(Objects.requireNonNull(json, "geometria nula"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Geometria GeoJSON inválida na projeção do mapa",
                    exception);
        }
    }

    /** O nome do setor é rótulo: sem nome, a ordem do mapeamento identifica. */
    private static String sectorLabel(String name, int displayOrder) {
        return name == null || name.isBlank() ? "Setor " + displayOrder : name;
    }
}
