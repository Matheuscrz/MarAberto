package com.maraberto.media.infra;

import com.maraberto.media.domain.MediaAsset;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persistência da mídia. O binário vive no storage de objetos; a linha guarda
 * só o endereço, as dimensões e o hash do conteúdo normalizado.
 */
@Repository
public class JdbcMediaRepository {

    private static final String COLUMNS = """
            id, object_key, content_type, size_bytes, width_px, height_px,
            sha256, owner_id, created_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMediaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MediaAsset insert(
            String objectKey,
            String contentType,
            long sizeBytes,
            Integer widthPx,
            Integer heightPx,
            byte[] sha256,
            UUID ownerId) {
        return jdbc.queryForObject("""
                        INSERT INTO media_assets
                            (object_key, content_type, size_bytes, width_px, height_px,
                             sha256, owner_id, status)
                        VALUES (:objectKey, :contentType, :sizeBytes, :widthPx, :heightPx,
                                :sha256, :ownerId, 'READY')
                        RETURNING %s
                        """.formatted(COLUMNS),
                new MapSqlParameterSource()
                        .addValue("objectKey", objectKey)
                        .addValue("contentType", contentType)
                        .addValue("sizeBytes", sizeBytes)
                        .addValue("widthPx", widthPx)
                        .addValue("heightPx", heightPx)
                        .addValue("sha256", sha256)
                        .addValue("ownerId", ownerId),
                (rs, rowNum) -> row(rs));
    }

    public Optional<MediaAsset> findById(UUID id) {
        return jdbc.query(
                        "SELECT " + COLUMNS + " FROM media_assets WHERE id = :id",
                        new MapSqlParameterSource("id", id),
                        (rs, rowNum) -> row(rs))
                .stream()
                .findFirst();
    }

    /** Ids da coleção que existem e pertencem ao operador informado. */
    public List<UUID> findOwnedIds(UUID ownerId, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT id FROM media_assets WHERE owner_id = :ownerId AND id IN (:ids)",
                new MapSqlParameterSource()
                        .addValue("ownerId", ownerId)
                        .addValue("ids", ids),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
    }

    private static MediaAsset row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MediaAsset(
                rs.getObject("id", UUID.class),
                rs.getString("object_key"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getObject("width_px", Integer.class),
                rs.getObject("height_px", Integer.class),
                rs.getBytes("sha256"),
                rs.getObject("owner_id", UUID.class),
                instant(rs, "created_at"));
    }

    private static Instant instant(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
