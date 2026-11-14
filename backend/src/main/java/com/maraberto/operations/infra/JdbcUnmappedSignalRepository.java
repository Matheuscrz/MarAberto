package com.maraberto.operations.infra;

import com.maraberto.operations.api.UnmappedSignalView;
import com.maraberto.operations.domain.FlagColor;
import com.maraberto.operations.domain.UnmappedSignalStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Fila de triagem da sinalização registrada fora do mapeamento (RF12/RN17).
 *
 * <p>O ponto é resolvido uma única vez, no lançamento: nenhuma rotina atualiza
 * a coordenada depois. A área mais próxima é calculada na leitura, como
 * sugestão para a coordenação — o vínculo só existe quando ela decide e fica
 * gravado em {@code beach_id}/{@code sector_id}.
 */
@Repository
public class JdbcUnmappedSignalRepository {

    /** Colunas lidas nas respostas e na listagem da triagem. */
    private static final String SELECT_COLUMNS = """
            u.id, u.flag, u.notes,
            ST_Y(u.location::geometry) AS latitude,
            ST_X(u.location::geometry) AS longitude,
            u.location_accuracy_m,
            ou.badge_number AS reported_by_badge,
            u.reported_post_id,
            p.name AS reported_post_name,
            u.status,
            u.beach_id,
            b.name AS beach_name,
            u.sector_id,
            s.name AS sector_name,
            s.display_order,
            n.id AS nearest_beach_id,
            n.name AS nearest_beach_name,
            n.distance_m AS nearest_distance_m,
            u.decision_notes,
            u.created_at,
            u.decided_at,
            ARRAY(
                SELECT '/api/v1/media/' || m.media_id
                  FROM unmapped_signal_media m
                 WHERE m.signal_id = u.id
                 ORDER BY m.display_order, m.media_id
            ) AS photo_urls
            FROM unmapped_signals u
            JOIN operational_users ou ON ou.id = u.reported_by
            LEFT JOIN lifeguard_posts p ON p.id = u.reported_post_id
            LEFT JOIN beaches b ON b.id = u.beach_id
            LEFT JOIN beach_sectors s ON s.id = u.sector_id
            LEFT JOIN LATERAL (
                SELECT nearest.id, nearest.name,
                       ST_Distance(nearest.boundary::geography, u.location) AS distance_m
                  FROM beaches nearest
                 WHERE nearest.active
                 ORDER BY nearest.boundary::geography <-> u.location
                 LIMIT 1
            ) n ON TRUE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcUnmappedSignalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Registra o lançamento feito fora do mapeamento e devolve o identificador. */
    public UUID insert(
            UUID reportedBy,
            UUID reportedPostId,
            FlagColor flag,
            String notes,
            double latitude,
            double longitude,
            Double accuracyMeters) {
        return jdbc.queryForObject("""
                        INSERT INTO unmapped_signals
                            (flag, notes, location, location_accuracy_m,
                             reported_by, reported_post_id)
                        VALUES (:flag, :notes,
                                CAST(ST_SetSRID(ST_MakePoint(
                                        CAST(:longitude AS double precision),
                                        CAST(:latitude AS double precision)), 4326)
                                    AS geography),
                                CAST(:accuracy AS numeric(7, 1)),
                                :reportedBy, :reportedPostId)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("flag", flag.name())
                        .addValue("notes", notes)
                        .addValue("latitude", latitude)
                        .addValue("longitude", longitude)
                        .addValue("accuracy", accuracyMeters)
                        .addValue("reportedBy", reportedBy)
                        .addValue("reportedPostId", reportedPostId),
                UUID.class);
    }

    /** Anexa as fotos ao registro, na ordem em que foram enviadas. */
    public void attachMedia(UUID signalId, List<UUID> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        for (int index = 0; index < mediaIds.size(); index++) {
            jdbc.update("""
                            INSERT INTO unmapped_signal_media (signal_id, media_id, display_order)
                            VALUES (:signalId, :mediaId, :displayOrder)
                            ON CONFLICT (signal_id, media_id) DO NOTHING
                            """,
                    new MapSqlParameterSource()
                            .addValue("signalId", signalId)
                            .addValue("mediaId", mediaIds.get(index))
                            .addValue("displayOrder", index));
        }
    }

    /**
     * Registros da fila, das mais recentes para as mais antigas.
     *
     * @param status estado filtrado, ou {@code null} para todos
     */
    public List<UnmappedSignalView> findAll(UnmappedSignalStatus status, int limit) {
        return jdbc.query(
                "SELECT " + SELECT_COLUMNS
                        + " WHERE (CAST(:status AS text) IS NULL OR u.status = CAST(:status AS text))"
                        + " ORDER BY u.created_at DESC, u.id LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("status", status == null ? null : status.name())
                        .addValue("limit", limit),
                (rs, rowNum) -> view(rs));
    }

    public Optional<UnmappedSignalView> findById(UUID id) {
        return jdbc.query(
                        "SELECT " + SELECT_COLUMNS + " WHERE u.id = :id",
                        new MapSqlParameterSource("id", id),
                        (rs, rowNum) -> view(rs))
                .stream()
                .findFirst();
    }

    /**
     * Grava a decisão da coordenação. {@code PENDING} reabre o registro e limpa
     * área, setor, justificativa e autoria da decisão anterior.
     */
    public void decide(
            UUID id,
            UnmappedSignalStatus status,
            UUID beachId,
            UUID sectorId,
            String notes,
            UUID decidedBy,
            Instant decidedAt) {
        boolean reopened = status == UnmappedSignalStatus.PENDING;
        jdbc.update("""
                        UPDATE unmapped_signals
                           SET status = :status,
                               beach_id = :beachId,
                               sector_id = :sectorId,
                               decision_notes = :notes,
                               decided_by = :decidedBy,
                               decided_at = :decidedAt
                         WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status.name())
                        .addValue("beachId", reopened ? null : beachId)
                        .addValue("sectorId", reopened ? null : sectorId)
                        .addValue("notes", reopened ? null : notes)
                        .addValue("decidedBy", reopened ? null : decidedBy)
                        .addValue("decidedAt", reopened ? null : java.sql.Timestamp.from(decidedAt)));
    }

    private static UnmappedSignalView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.math.BigDecimal accuracy = rs.getObject("location_accuracy_m",
                java.math.BigDecimal.class);
        Double distance = rs.getObject("nearest_distance_m", Double.class);
        return new UnmappedSignalView(
                rs.getObject("id", UUID.class),
                FlagColor.valueOf(rs.getString("flag")),
                rs.getString("notes"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                accuracy == null ? null : accuracy.doubleValue(),
                rs.getString("reported_by_badge"),
                rs.getObject("reported_post_id", UUID.class),
                rs.getString("reported_post_name"),
                UnmappedSignalStatus.valueOf(rs.getString("status")),
                rs.getObject("beach_id", UUID.class),
                rs.getString("beach_name"),
                rs.getObject("sector_id", UUID.class),
                sectorLabel(rs.getString("sector_name"), rs.getInt("display_order")),
                rs.getObject("nearest_beach_id", UUID.class),
                rs.getString("nearest_beach_name"),
                distance == null ? null : Math.round(distance.doubleValue()),
                rs.getString("decision_notes"),
                instant(rs, "created_at"),
                instant(rs, "decided_at"),
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

    /** {@code getObject(..., Instant.class)} não é suportado pelo driver. */
    private static Instant instant(java.sql.ResultSet rs, String column)
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
