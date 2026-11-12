package com.maraberto.beaches.infra;

import com.maraberto.beaches.api.BeachDetail;
import com.maraberto.beaches.api.BeachSectorSummary;
import com.maraberto.beaches.api.BeachSummary;
import com.maraberto.beaches.domain.BeachKind;
import java.util.List;
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

    public PublicBeachRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
}
