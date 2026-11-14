package com.maraberto.operations.infra;

import com.maraberto.operations.api.PublicFlag;
import com.maraberto.operations.api.PublicStation;
import com.maraberto.operations.domain.FlagColor;
import com.maraberto.operations.domain.StationKind;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Leitura pública das estruturas e da sinalização (RF01/RF12/RF38).
 *
 * <p>Vigência é decidida aqui, na consulta, e não por mutação: nada é apagado
 * nem desativado por tempo. Posto inativo some, base móvel sem plantão vivo
 * some e bandeira vencida ou encerrada some — o histórico continua intacto.
 */
@Repository
public class PublicOperationsRepository {

    /**
     * Postos fixos ativos e bases móveis com plantão vivo.
     *
     * <p>A base móvel só entra com plantão aberto e dentro do lease; a posição
     * é a última conhecida do turno (nunca rastreamento contínuo — RN17) e cai
     * para a posição cadastrada quando o console ainda não mandou GPS.
     */
    private static final String STATIONS_SQL = """
            SELECT p.id,
                   'FIXED' AS kind,
                   p.name,
                   b.name AS beach_name,
                   ST_Y(p.location::geometry) AS latitude,
                   ST_X(p.location::geometry) AS longitude,
                   NULL::timestamptz AS position_updated_at,
                   ARRAY(
                       SELECT '/api/v1/media/' || pm.media_id
                         FROM lifeguard_post_media pm
                        WHERE pm.post_id = p.id
                        ORDER BY pm.display_order, pm.media_id
                   ) AS photo_urls
              FROM lifeguard_posts p
              JOIN beaches b ON b.id = p.beach_id
             WHERE p.active
               AND b.active
            UNION ALL
            SELECT m.id,
                   'MOBILE' AS kind,
                   m.name,
                   NULL::text AS beach_name,
                   ST_Y(COALESCE(s.last_position, m.location)::geometry) AS latitude,
                   ST_X(COALESCE(s.last_position, m.location)::geometry) AS longitude,
                   COALESCE(s.last_position_at, s.last_seen_at) AS position_updated_at,
                   ARRAY(
                       SELECT '/api/v1/media/' || mm.media_id
                         FROM mobile_base_media mm
                        WHERE mm.base_id = m.id
                        ORDER BY mm.display_order, mm.media_id
                   ) AS photo_urls
              FROM mobile_bases m
              JOIN LATERAL (
                  SELECT s.last_position, s.last_position_at, s.last_seen_at
                    FROM shift_sessions s
                   WHERE s.mobile_base_id = m.id
                     AND s.ended_at IS NULL
                     AND s.last_seen_at >= NOW() - make_interval(secs => :idleSeconds)
                   ORDER BY s.last_seen_at DESC
                   LIMIT 1
              ) s ON TRUE
             WHERE m.active
             ORDER BY kind, name
            """;

    /**
     * Bandeiras vigentes, da mais recente para a mais antiga.
     *
     * <p>O pin do setor é o centroide do polígono; o da praia inteira é um
     * ponto interno da área. O aparelho do guarda-vidas não vira pin.
     */
    private static final String FLAG_COLUMNS = """
                   f.id,
                   f.beach_id,
                   b.name AS beach_name,
                   f.sector_id,
                   s.name AS sector_name,
                   s.display_order,
                   f.flag,
                   f.notes,
                   CASE
                       WHEN f.signal_position IS NOT NULL
                           THEN ST_Y(f.signal_position::geometry)
                       WHEN f.sector_id IS NOT NULL THEN ST_Y(ST_Centroid(s.geometry))
                       ELSE ST_Y(ST_PointOnSurface(b.boundary))
                   END AS latitude,
                   CASE
                       WHEN f.signal_position IS NOT NULL
                           THEN ST_X(f.signal_position::geometry)
                       WHEN f.sector_id IS NOT NULL THEN ST_X(ST_Centroid(s.geometry))
                       ELSE ST_X(ST_PointOnSurface(b.boundary))
                   END AS longitude,
                   p.name AS station_name,
                   f.created_at,
                   f.valid_until,
                   f.closed_at,
                   ARRAY(
                       SELECT '/api/v1/media/' || fm.media_id
                         FROM flag_media fm
                        WHERE fm.flag_id = f.id
                        ORDER BY fm.display_order, fm.media_id
                   ) AS photo_urls
            """;

    /** Bandeira vigente: aberta e dentro da validade. */
    private static final String FLAGS_SQL = """
            SELECT %s
              FROM flag_updates f
              JOIN beaches b ON b.id = f.beach_id AND b.active
              LEFT JOIN beach_sectors s ON s.id = f.sector_id AND s.active
              LEFT JOIN lifeguard_posts p ON p.id = f.post_id
             WHERE f.closed_at IS NULL
               AND f.valid_until > NOW()
               AND (f.sector_id IS NULL OR s.id IS NOT NULL)
               AND (CAST(:beachId AS uuid) IS NULL OR f.beach_id = CAST(:beachId AS uuid))
             ORDER BY f.created_at DESC, f.id
            """.formatted(FLAG_COLUMNS);

    /**
     * Último lançamento de cada escopo (praia inteira e cada setor), vigente ou
     * não. A ficha da praia mostra a última cor conhecida com o selo de
     * "desatualizada" quando ela venceu ou foi encerrada (RF12/RN01).
     */
    private static final String LATEST_FLAGS_SQL = """
            SELECT *
              FROM (
                    SELECT DISTINCT ON (f.sector_id) %s
                      FROM flag_updates f
                      JOIN beaches b ON b.id = f.beach_id AND b.active
                      LEFT JOIN beach_sectors s ON s.id = f.sector_id AND s.active
                      LEFT JOIN lifeguard_posts p ON p.id = f.post_id
                     WHERE f.beach_id = :beachId
                       AND (f.sector_id IS NULL OR s.id IS NOT NULL)
                     ORDER BY f.sector_id NULLS FIRST, f.created_at DESC, f.id DESC
              ) latest
             ORDER BY created_at DESC, sector_id
            """.formatted(FLAG_COLUMNS);

    private final NamedParameterJdbcTemplate jdbc;
    private final Duration idleTimeout;

    public PublicOperationsRepository(
            NamedParameterJdbcTemplate jdbc,
            @Value("${maraberto.dispatch.shift-idle-timeout:PT15M}") Duration idleTimeout) {
        this.jdbc = jdbc;
        this.idleTimeout = idleTimeout;
    }

    public List<PublicStation> findStations() {
        return jdbc.query(STATIONS_SQL,
                new MapSqlParameterSource("idleSeconds", idleTimeout.toSeconds()),
                (rs, rowNum) -> new PublicStation(
                        rs.getObject("id", UUID.class),
                        StationKind.valueOf(rs.getString("kind")),
                        rs.getString("name"),
                        rs.getString("beach_name"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        instant(rs, "position_updated_at"),
                        photoUrls(rs)));
    }

    /**
     * Bandeiras vigentes.
     *
     * @param beachId área específica ou {@code null} para todas
     */
    public List<PublicFlag> findActiveFlags(UUID beachId) {
        return jdbc.query(FLAGS_SQL,
                new MapSqlParameterSource("beachId", beachId),
                (rs, rowNum) -> flagRow(rs));
    }

    /** Último lançamento de cada escopo da área, incluindo vencidos e encerrados. */
    public List<PublicFlag> findLatestFlags(UUID beachId) {
        return jdbc.query(LATEST_FLAGS_SQL,
                new MapSqlParameterSource("beachId", beachId),
                (rs, rowNum) -> flagRow(rs));
    }

    private static PublicFlag flagRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PublicFlag(
                rs.getObject("id", UUID.class),
                rs.getObject("beach_id", UUID.class),
                rs.getString("beach_name"),
                rs.getObject("sector_id", UUID.class),
                sectorLabel(rs.getString("sector_name"), rs.getInt("display_order")),
                FlagColor.valueOf(rs.getString("flag")),
                rs.getString("notes"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getString("station_name"),
                instant(rs, "created_at"),
                instant(rs, "valid_until"),
                instant(rs, "closed_at"),
                photoUrls(rs));
    }

    private static List<String> photoUrls(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Array array = rs.getArray("photo_urls");
        if (array == null) {
            return List.of();
        }
        Object value = array.getArray();
        return value instanceof String[] urls ? List.of(urls) : List.of();
    }

    /** {@code getObject(..., Instant.class)} não é suportado pelo driver; o timestamp é convertido aqui. */
    private static java.time.Instant instant(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /** Nome do setor é rótulo: sem nome, a ordem do mapeamento identifica. */
    private static String sectorLabel(String name, int displayOrder) {
        if (name == null || name.isBlank()) {
            return displayOrder > 0 ? "Setor " + displayOrder : null;
        }
        return name;
    }
}
