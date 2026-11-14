package com.maraberto.operations.infra;

import com.maraberto.operations.api.FlagResponse;
import com.maraberto.operations.domain.FlagColor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Escrita da sinalização. {@code flag_updates} é append-only: lançar de novo no
 * mesmo escopo fecha o anterior e insere uma linha nova.
 */
@Repository
public class JdbcFlagRepository {

    /** Colunas lidas por id, com as fotos do lançamento. */
    private static final String SELECT_COLUMNS = """
            f.id, f.beach_id, f.sector_id, f.flag,
            f.created_at, f.valid_until, f.closed_at,
            ARRAY(
                SELECT '/api/v1/media/' || fm.media_id
                  FROM flag_media fm
                 WHERE fm.flag_id = f.id
                 ORDER BY fm.display_order, fm.media_id
            ) AS photo_urls
            """;

    /** Mesmas colunas no {@code RETURNING}: sem alias, e sem foto (nada anexado ainda). */
    private static final String RETURNING_COLUMNS = """
            id, beach_id, sector_id, flag,
            created_at, valid_until, closed_at,
            ARRAY[]::text[] AS photo_urls
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFlagRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Serializa lançamentos do mesmo escopo dentro da transação.
     *
     * <p>Sem o lock, dois guarda-vidas lançando ao mesmo tempo poderiam deixar
     * duas bandeiras abertas para o mesmo setor: o {@code UPDATE} de
     * encerramento não enxerga a linha que a outra transação ainda não
     * confirmou.
     */
    public void lockScope(UUID beachId, UUID sectorId) {
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(:scope, 0))",
                new MapSqlParameterSource("scope", scopeKey(beachId, sectorId)),
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    // A função não devolve dado: basta executar.
                });
    }

    /** Encerra o lançamento vigente do escopo, se houver. */
    public void closeScope(UUID beachId, UUID sectorId, UUID closedBy, Instant when) {
        jdbc.update("""
                        UPDATE flag_updates
                           SET closed_at = :when,
                               closed_by = :closedBy
                         WHERE beach_id = :beachId
                           AND sector_id IS NOT DISTINCT FROM :sectorId
                           AND closed_at IS NULL
                        """,
                new MapSqlParameterSource()
                        .addValue("beachId", beachId)
                        .addValue("sectorId", sectorId)
                        .addValue("closedBy", closedBy)
                        .addValue("when", java.sql.Timestamp.from(when)));
    }

    public FlagResponse insert(
            UUID beachId,
            UUID sectorId,
            UUID postId,
            FlagColor flag,
            String notes,
            UUID setBy,
            Instant createdAt,
            Instant validUntil,
            Double latitude,
            Double longitude,
            Double accuracyMeters,
            String positionSource) {
        return jdbc.queryForObject("""
                        INSERT INTO flag_updates
                            (beach_id, sector_id, post_id, flag, notes,
                             set_by, valid_until, created_at,
                             signal_position, position_accuracy_m, position_source)
                        VALUES (:beachId, :sectorId, :postId, :flag, :notes,
                                :setBy, :validUntil, :createdAt,
                                CASE
                                    WHEN CAST(:latitude AS double precision) IS NULL THEN NULL
                                    ELSE CAST(ST_SetSRID(ST_MakePoint(
                                            CAST(:longitude AS double precision),
                                            CAST(:latitude AS double precision)), 4326) AS geography)
                                END,
                                CAST(:accuracy AS numeric(7, 1)),
                                :positionSource)
                        RETURNING %s
                        """.formatted(RETURNING_COLUMNS),
                new MapSqlParameterSource()
                        .addValue("beachId", beachId)
                        .addValue("sectorId", sectorId)
                        .addValue("postId", postId)
                        .addValue("flag", flag.name())
                        .addValue("notes", notes)
                        .addValue("setBy", setBy)
                        .addValue("validUntil", java.sql.Timestamp.from(validUntil))
                        .addValue("createdAt", java.sql.Timestamp.from(createdAt))
                        .addValue("latitude", latitude)
                        .addValue("longitude", longitude)
                        .addValue("accuracy", accuracyMeters)
                        .addValue("positionSource", positionSource),
                (rs, rowNum) -> row(rs));
    }

    /** Anexa as fotos ao lançamento, na ordem em que foram enviadas. */
    public void attachMedia(UUID flagId, List<UUID> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        for (int index = 0; index < mediaIds.size(); index++) {
            jdbc.update("""
                            INSERT INTO flag_media (flag_id, media_id, display_order)
                            VALUES (:flagId, :mediaId, :displayOrder)
                            ON CONFLICT (flag_id, media_id) DO NOTHING
                            """,
                    new MapSqlParameterSource()
                            .addValue("flagId", flagId)
                            .addValue("mediaId", mediaIds.get(index))
                            .addValue("displayOrder", index));
        }
    }

    public Optional<FlagResponse> findById(UUID id) {
        return jdbc.query(
                        "SELECT " + SELECT_COLUMNS + " FROM flag_updates f WHERE f.id = :id",
                        new MapSqlParameterSource("id", id),
                        (rs, rowNum) -> row(rs))
                .stream()
                .findFirst();
    }

    public void close(UUID id, UUID closedBy, Instant when) {
        jdbc.update("""
                        UPDATE flag_updates
                           SET closed_at = :when,
                               closed_by = :closedBy
                         WHERE id = :id
                           AND closed_at IS NULL
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("closedBy", closedBy)
                        .addValue("when", java.sql.Timestamp.from(when)));
    }

    private static FlagResponse row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FlagResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("beach_id", UUID.class),
                rs.getObject("sector_id", UUID.class),
                FlagColor.valueOf(rs.getString("flag")),
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
        if (value instanceof String[] urls) {
            return List.of(urls);
        }
        return List.of();
    }

    private static Instant instant(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String scopeKey(UUID beachId, UUID sectorId) {
        return "flags:" + beachId + ":" + (sectorId == null ? "beach" : sectorId);
    }
}
