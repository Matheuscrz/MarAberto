package com.maraberto.operations.infra;

import com.maraberto.operations.api.PublicFlag;
import com.maraberto.operations.api.PublicStation;
import com.maraberto.operations.api.PublicUnmappedSignal;
import com.maraberto.operations.domain.FlagColor;
import com.maraberto.operations.domain.StationKind;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Leitura pública das estruturas e da sinalização (RF01/RF12/RF38).
 *
 * <p>Vigência é decidida aqui, na consulta, e não por mutação: nada é apagado
 * nem desativado por tempo. Posto e base inativos somem, bandeira vencida ou
 * encerrada some e alerta de área não mapeada sai quando vence ou é triado — o
 * histórico continua intacto.
 */
@Repository
public class PublicOperationsRepository {

    /**
     * Postos fixos e bases móveis ativos.
     *
     * <p>A base móvel é uma estrutura própria: aparece no mapa enquanto estiver
     * ativa, na posição em que foi cadastrada. Ela não depende de plantão vivo
     * nem segue o guarda-vidas — a última posição do turno é do plantão e
     * aparece como pin de guarda-vidas no mapa de situação (RN17).
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
                   ST_Y(m.location::geometry) AS latitude,
                   ST_X(m.location::geometry) AS longitude,
                   m.position_updated_at,
                   ARRAY(
                       SELECT '/api/v1/media/' || mm.media_id
                         FROM mobile_base_media mm
                        WHERE mm.base_id = m.id
                        ORDER BY mm.display_order, mm.media_id
                   ) AS photo_urls
              FROM mobile_bases m
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

    /**
     * Alertas de sinalização fora do mapeamento, para o mapa público.
     *
     * <p>O pin é o ponto do lançamento — não há praia nem setor para herdar.
     * Só entra o que está pendente de triagem e dentro da validade; decidir ou
     * vencer tira o alerta do mapa, mas o registro continua na fila.
     */
    private static final String UNMAPPED_ALERTS_SQL = """
            SELECT u.id,
                   u.flag,
                   u.notes,
                   ST_Y(u.location::geometry) AS latitude,
                   ST_X(u.location::geometry) AS longitude,
                   u.location_accuracy_m,
                   u.created_at,
                   u.valid_until,
                   ARRAY(
                       SELECT '/api/v1/media/' || m.media_id
                         FROM unmapped_signal_media m
                        WHERE m.signal_id = u.id
                        ORDER BY m.display_order, m.media_id
                   ) AS photo_urls
              FROM unmapped_signals u
             WHERE u.status = 'PENDING'
               AND u.valid_until > NOW()
             ORDER BY u.created_at DESC, u.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PublicOperationsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PublicStation> findStations() {
        return jdbc.query(STATIONS_SQL, new MapSqlParameterSource(),
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

    /**
     * Alertas de sinalização lançada fora do mapeamento.
     *
     * <p>Só o que está pendente de triagem e dentro da validade: o alerta não
     * espera decisão para aparecer, e sai do mapa quando vence. A fila de
     * triagem continua com o registro completo em {@code /management}.
     */
    public List<PublicUnmappedSignal> findActiveUnmappedAlerts() {
        return jdbc.query(UNMAPPED_ALERTS_SQL, new MapSqlParameterSource(),
                (rs, rowNum) -> {
                    java.math.BigDecimal accuracy = rs.getObject("location_accuracy_m",
                            java.math.BigDecimal.class);
                    return new PublicUnmappedSignal(
                            rs.getObject("id", UUID.class),
                            FlagColor.valueOf(rs.getString("flag")),
                            rs.getString("notes"),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude"),
                            accuracy == null ? null : accuracy.doubleValue(),
                            instant(rs, "created_at"),
                            instant(rs, "valid_until"),
                            photoUrls(rs));
                });
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
