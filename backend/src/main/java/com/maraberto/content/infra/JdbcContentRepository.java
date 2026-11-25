package com.maraberto.content.infra;

import com.maraberto.content.api.ContentKind;
import com.maraberto.content.api.ContentView;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlValue;
import org.springframework.stereotype.Repository;

/**
 * Persistência JDBC do catálogo de conteúdos educativos e materiais de divulgação.
 */
@Repository
public class JdbcContentRepository {

    private static final String COLUMNS = """
            id, kind, title, description, category, tags,
            external_url, external_link_label, youtube_id, duration,
            display_order, published, created_at, updated_at
            """;

    private static final RowMapper<ContentView> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcContentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ContentView> listPublic(ContentKind kind) {
        StringBuilder sql = new StringBuilder("""
                SELECT\s""").append(COLUMNS).append("""
                  FROM educational_content
                 WHERE published = TRUE
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (kind != null) {
            sql.append("   AND kind = :kind\n");
            params.addValue("kind", kind.name());
        }
        sql.append(" ORDER BY display_order ASC, created_at DESC");
        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    public List<ContentView> listAll(ContentKind kind, Boolean published) {
        StringBuilder sql = new StringBuilder("""
                SELECT\s""").append(COLUMNS).append("""
                  FROM educational_content
                 WHERE 1 = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (kind != null) {
            sql.append("   AND kind = :kind\n");
            params.addValue("kind", kind.name());
        }
        if (published != null) {
            sql.append("   AND published = :published\n");
            params.addValue("published", published);
        }
        sql.append(" ORDER BY display_order ASC, created_at DESC");
        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    public Optional<ContentView> findById(UUID id) {
        List<ContentView> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM educational_content WHERE id = :id",
                new MapSqlParameterSource("id", id),
                ROW_MAPPER);
        return rows.stream().findFirst();
    }

    public int nextDisplayOrder() {
        Integer next = jdbc.getJdbcOperations().queryForObject(
                "SELECT COALESCE(MAX(display_order), -1) + 1 FROM educational_content",
                Integer.class);
        return next == null ? 0 : next;
    }

    public ContentView insert(
            UUID id,
            ContentKind kind,
            String title,
            String description,
            String category,
            List<String> tags,
            String externalUrl,
            String externalLinkLabel,
            String youtubeId,
            String duration,
            int displayOrder,
            boolean published,
            UUID actorId,
            Instant now) {
        Timestamp ts = Timestamp.from(now);
        return jdbc.queryForObject("""
                        INSERT INTO educational_content (
                            id, kind, title, description, category, tags,
                            external_url, external_link_label, youtube_id, duration,
                            display_order, published, created_by, updated_by, created_at, updated_at
                        ) VALUES (
                            :id, :kind, :title, :description, :category, :tags,
                            :externalUrl, :externalLinkLabel, :youtubeId, :duration,
                            :displayOrder, :published, :actorId, :actorId, :now, :now
                        )
                        RETURNING\s""" + COLUMNS,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("kind", kind.name())
                        .addValue("title", title)
                        .addValue("description", description)
                        .addValue("category", category)
                        .addValue("tags", textArray(tags))
                        .addValue("externalUrl", externalUrl)
                        .addValue("externalLinkLabel", externalLinkLabel)
                        .addValue("youtubeId", youtubeId)
                        .addValue("duration", duration)
                        .addValue("displayOrder", displayOrder)
                        .addValue("published", published)
                        .addValue("actorId", actorId)
                        .addValue("now", ts),
                ROW_MAPPER);
    }

    public Optional<ContentView> update(
            UUID id,
            ContentKind kind,
            String title,
            String description,
            String category,
            List<String> tags,
            String externalUrl,
            String externalLinkLabel,
            String youtubeId,
            String duration,
            int displayOrder,
            boolean published,
            UUID actorId,
            Instant now) {
        List<ContentView> rows = jdbc.query("""
                        UPDATE educational_content
                           SET kind = :kind,
                               title = :title,
                               description = :description,
                               category = :category,
                               tags = :tags,
                               external_url = :externalUrl,
                               external_link_label = :externalLinkLabel,
                               youtube_id = :youtubeId,
                               duration = :duration,
                               display_order = :displayOrder,
                               published = :published,
                               updated_by = :actorId,
                               updated_at = :now
                         WHERE id = :id
                        RETURNING\s""" + COLUMNS,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("kind", kind.name())
                        .addValue("title", title)
                        .addValue("description", description)
                        .addValue("category", category)
                        .addValue("tags", textArray(tags))
                        .addValue("externalUrl", externalUrl)
                        .addValue("externalLinkLabel", externalLinkLabel)
                        .addValue("youtubeId", youtubeId)
                        .addValue("duration", duration)
                        .addValue("displayOrder", displayOrder)
                        .addValue("published", published)
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)),
                ROW_MAPPER);
        return rows.stream().findFirst();
    }

    public Optional<ContentView> updatePublished(
            UUID id, boolean published, UUID actorId, Instant now) {
        List<ContentView> rows = jdbc.query("""
                        UPDATE educational_content
                           SET published = :published,
                               updated_by = :actorId,
                               updated_at = :now
                         WHERE id = :id
                        RETURNING\s""" + COLUMNS,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("published", published)
                        .addValue("actorId", actorId)
                        .addValue("now", Timestamp.from(now)),
                ROW_MAPPER);
        return rows.stream().findFirst();
    }

    public int reorder(List<UUID> orderedIds, UUID actorId, Instant now) {
        Timestamp ts = Timestamp.from(now);
        MapSqlParameterSource[] batch = new MapSqlParameterSource[orderedIds.size()];
        for (int i = 0; i < orderedIds.size(); i++) {
            batch[i] = new MapSqlParameterSource()
                    .addValue("id", orderedIds.get(i))
                    .addValue("displayOrder", i)
                    .addValue("actorId", actorId)
                    .addValue("now", ts);
        }
        int[] updated = jdbc.batchUpdate("""
                UPDATE educational_content
                   SET display_order = :displayOrder,
                       updated_by = :actorId,
                       updated_at = :now
                 WHERE id = :id
                """, batch);
        int total = 0;
        for (int count : updated) {
            total += count;
        }
        return total;
    }

    public boolean deleteById(UUID id) {
        int rows = jdbc.update(
                "DELETE FROM educational_content WHERE id = :id",
                new MapSqlParameterSource("id", id));
        return rows > 0;
    }

    private static SqlValue textArray(List<String> values) {
        String[] raw = values.toArray(String[]::new);
        return new SqlValue() {
            @Override
            public void setValue(PreparedStatement ps, int paramIndex) throws SQLException {
                ps.setArray(paramIndex, ps.getConnection().createArrayOf("text", raw));
            }

            @Override
            public void cleanup() {
                // Nada a liberar.
            }
        };
    }

    private static ContentView mapRow(ResultSet rs) throws SQLException {
        return new ContentView(
                rs.getObject("id", UUID.class),
                ContentKind.valueOf(rs.getString("kind")),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("category"),
                readTags(rs.getArray("tags")),
                rs.getString("external_url"),
                rs.getString("external_link_label"),
                rs.getString("youtube_id"),
                rs.getString("duration"),
                rs.getInt("display_order"),
                rs.getBoolean("published"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static List<String> readTags(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object raw = array.getArray();
        if (raw instanceof String[] strings) {
            return Arrays.asList(strings);
        }
        return List.of();
    }
}
