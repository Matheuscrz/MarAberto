package com.maraberto.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.audit.api.AuditEntryView;
import com.maraberto.audit.api.AuditPage;
import com.maraberto.audit.domain.InvalidAuditCursorException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta paginada da trilha para a TI (RNF06, ADR-027).
 *
 * <p>A ordenação e o cursor são {@code (occurred_at, id)} decrescentes: a
 * chave é estável mesmo quando novos atos chegam entre duas páginas. Nenhum
 * filtro é aplicado no cliente.
 */
@Service
public class AuditQueryService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;

    private static final TypeReference<Map<String, Object>> DETAIL_TYPE =
            new TypeReference<>() {
            };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditQueryService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AuditPage list(
            UUID actorId,
            String action,
            String entityType,
            String entityId,
            String traceId,
            Instant from,
            Instant to,
            String cursor,
            int limit) {
        int size = clampLimit(limit);
        AuditCursor after = AuditCursor.decode(cursor);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("actorId", actorId, Types.OTHER)
                .addValue("action", blankToNull(action), Types.VARCHAR)
                .addValue("entityType", blankToNull(entityType), Types.VARCHAR)
                .addValue("entityId", blankToNull(entityId), Types.VARCHAR)
                .addValue("traceId", blankToNull(traceId), Types.VARCHAR)
                .addValue("from", from == null ? null : Timestamp.from(from),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", to == null ? null : Timestamp.from(to),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("cursorAt",
                        after == null ? null : Timestamp.from(after.occurredAt()),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("cursorId", after == null ? null : after.id(), Types.BIGINT)
                .addValue("limit", size + 1, Types.INTEGER);

        List<AuditEntryView> rows = new ArrayList<>(jdbc.query("""
                        SELECT id,
                               occurred_at,
                               actor_id,
                               actor_role,
                               action,
                               entity_type,
                               entity_id,
                               trace_id,
                               detail::text AS detail
                          FROM audit_log
                         WHERE (CAST(:actorId AS uuid) IS NULL OR actor_id = CAST(:actorId AS uuid))
                           AND (CAST(:action AS text) IS NULL OR action = CAST(:action AS text))
                           AND (CAST(:entityType AS text) IS NULL
                                OR entity_type = CAST(:entityType AS text))
                           AND (CAST(:entityId AS text) IS NULL
                                OR entity_id = CAST(:entityId AS text))
                           AND (CAST(:traceId AS text) IS NULL
                                OR trace_id = CAST(:traceId AS text))
                           AND (CAST(:from AS timestamptz) IS NULL
                                OR occurred_at >= CAST(:from AS timestamptz))
                           AND (CAST(:to AS timestamptz) IS NULL
                                OR occurred_at < CAST(:to AS timestamptz))
                           AND (CAST(:cursorAt AS timestamptz) IS NULL
                                OR (occurred_at, id) < (
                                    CAST(:cursorAt AS timestamptz),
                                    CAST(:cursorId AS bigint)))
                         ORDER BY occurred_at DESC, id DESC
                         LIMIT :limit
                        """,
                params,
                (rs, rowNum) -> map(rs)));

        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, size);
        }
        AuditEntryView last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        String nextCursor = hasMore && last != null
                ? AuditCursor.encode(last.occurredAt(), last.id())
                : null;
        return new AuditPage(List.copyOf(rows), nextCursor, hasMore);
    }

    private AuditEntryView map(ResultSet rs) throws SQLException {
        return new AuditEntryView(
                rs.getLong("id"),
                rs.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_role"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("trace_id"),
                detail(rs.getString("detail")));
    }

    private Map<String, Object> detail(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, DETAIL_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("detalhe de auditoria inválido", exception);
        }
    }

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Cursor opaco de {@code (occurred_at, id)}.
     *
     * <p>O instante entra em segundos + nanossegundos para não perder a ordem
     * quando dois atos caem no mesmo milissegundo.
     */
    record AuditCursor(Instant occurredAt, long id) {

        static String encode(Instant occurredAt, long id) {
            String raw = occurredAt.getEpochSecond()
                    + ":" + occurredAt.getNano()
                    + ":" + id;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static AuditCursor decode(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return null;
            }
            try {
                String raw = new String(
                        Base64.getUrlDecoder().decode(cursor.trim()),
                        StandardCharsets.UTF_8);
                String[] parts = raw.split(":", 3);
                if (parts.length != 3) {
                    throw new InvalidAuditCursorException();
                }
                long seconds = Long.parseLong(parts[0]);
                int nanos = Integer.parseInt(parts[1]);
                long id = Long.parseLong(parts[2]);
                if (nanos < 0 || nanos > 999_999_999 || id < 0) {
                    throw new InvalidAuditCursorException();
                }
                return new AuditCursor(Instant.ofEpochSecond(seconds, nanos), id);
            } catch (IllegalArgumentException exception) {
                throw new InvalidAuditCursorException();
            }
        }
    }
}
