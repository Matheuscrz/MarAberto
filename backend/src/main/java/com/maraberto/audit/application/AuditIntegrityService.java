package com.maraberto.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.audit.api.AuditIntegrityView;
import com.maraberto.audit.domain.AuditHashChain;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confere o encadeamento de hashes da trilha.
 *
 * <p>Percorre a cadeia em ordem de escrita e recalcula cada linha a partir dos
 * campos persistidos. Divergência em {@code prev_hash} ou {@code hash} aponta a
 * primeira linha adulterada (ou removida) — a conferência é somente leitura.
 */
@Service
public class AuditIntegrityService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditIntegrityService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AuditIntegrityView verify() {
        String expectedPrev = AuditHashChain.GENESIS;
        long checked = 0;

        for (Row row : jdbc.query("""
                        SELECT id,
                               occurred_at,
                               actor_id,
                               actor_role,
                               action,
                               entity_type,
                               entity_id,
                               trace_id,
                               ip_bidx,
                               detail::text AS detail,
                               prev_hash,
                               hash
                          FROM audit_log
                         ORDER BY id ASC
                        """,
                (rs, rowNum) -> map(rs))) {
            if (!expectedPrev.equals(row.prevHash())) {
                return new AuditIntegrityView(checked, false, row.id());
            }
            String canonical = AuditHashChain.canonical(
                    row.occurredAt(),
                    row.actorId(),
                    row.actorRole(),
                    row.action(),
                    row.entityType(),
                    row.entityId(),
                    row.traceId(),
                    row.ipBidx(),
                    row.detail());
            String recalculated = AuditHashChain.hash(row.prevHash(), canonical);
            if (!recalculated.equals(row.hash())) {
                return new AuditIntegrityView(checked, false, row.id());
            }
            expectedPrev = row.hash();
            checked += 1;
        }
        return new AuditIntegrityView(checked, true, null);
    }

    private Row map(ResultSet rs) throws SQLException {
        return new Row(
                rs.getLong("id"),
                rs.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_role"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("trace_id"),
                rs.getString("ip_bidx"),
                rs.getString("detail"),
                rs.getString("prev_hash"),
                rs.getString("hash"));
    }

    private record Row(
            long id,
            Instant occurredAt,
            UUID actorId,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            String traceId,
            String ipBidx,
            String detail,
            String prevHash,
            String hash) {
    }
}
