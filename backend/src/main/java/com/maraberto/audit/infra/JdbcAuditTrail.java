package com.maraberto.audit.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.audit.api.AuditEvent;
import com.maraberto.audit.api.AuditTrail;
import com.maraberto.audit.domain.AuditHashChain;
import com.maraberto.shared.security.crypto.BlindIndexer;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrita da trilha no PostgreSQL com encadeamento de hash.
 *
 * <p>O advisory lock da transação serializa a leitura do último hash e o
 * insert: duas requisições concorrentes não conseguem apontar para o mesmo
 * predecessor. O {@code UNIQUE} de {@code prev_hash} é a segunda barreira.
 *
 * <p>A escrita participa da transação do ato auditado. Se o insert falhar, o
 * ato também falha — não existe ação sensível sem trilha.
 */
@Service
public class JdbcAuditTrail implements AuditTrail {

    /** Chave fixa do advisory lock de escrita da cadeia ("AUDI"). */
    private static final long CHAIN_LOCK_KEY = 0x41554449L;

    private static final String TRACE_ID = "traceId";
    private static final int MAX_TRACE_LENGTH = 64;

    private final NamedParameterJdbcTemplate jdbc;
    private final BlindIndexer blindIndexer;
    private final ObjectMapper objectMapper;

    public JdbcAuditTrail(
            NamedParameterJdbcTemplate jdbc,
            BlindIndexer blindIndexer,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.blindIndexer = blindIndexer;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void record(AuditEvent event) {
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String traceId = currentTraceId();
        String ipBidx = event.ipAddress() == null || event.ipAddress().isBlank()
                ? null
                : blindIndexer.index(event.ipAddress());
        String detailJson = AuditHashChain.detailJson(event.detail(), objectMapper);
        String actorRole = currentRole();

        jdbc.getJdbcTemplate().execute(
                "SELECT pg_advisory_xact_lock(" + CHAIN_LOCK_KEY + ")");
        // O hash precisa fechar com o texto que o PostgreSQL devolve em
        // `detail::text` (chaves reordenadas, espaçamento próprio). Canonizar
        // pelo próprio banco evita divergência entre escrita e conferência.
        String storedDetail = jdbc.queryForObject(
                "SELECT CAST(CAST(:detail AS jsonb) AS text)",
                new MapSqlParameterSource().addValue("detail", detailJson, Types.VARCHAR),
                String.class);
        String prevHash = lastHash();
        String canonical = AuditHashChain.canonical(
                occurredAt,
                event.actorId(),
                actorRole,
                event.action(),
                event.entityType(),
                event.entityId(),
                traceId,
                ipBidx,
                storedDetail);
        String hash = AuditHashChain.hash(prevHash, canonical);

        jdbc.update("""
                        INSERT INTO audit_log (
                            occurred_at,
                            actor_id,
                            actor_role,
                            action,
                            entity_type,
                            entity_id,
                            trace_id,
                            ip_bidx,
                            detail,
                            prev_hash,
                            hash
                        ) VALUES (
                            :occurredAt,
                            :actorId,
                            :actorRole,
                            :action,
                            :entityType,
                            :entityId,
                            :traceId,
                            :ipBidx,
                            CAST(:detail AS jsonb),
                            :prevHash,
                            :hash
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("occurredAt", Timestamp.from(occurredAt))
                        .addValue("actorId", event.actorId(), Types.OTHER)
                        .addValue("actorRole", actorRole, Types.VARCHAR)
                        .addValue("action", event.action(), Types.VARCHAR)
                        .addValue("entityType", event.entityType(), Types.VARCHAR)
                        .addValue("entityId", event.entityId(), Types.VARCHAR)
                        .addValue("traceId", traceId, Types.VARCHAR)
                        .addValue("ipBidx", ipBidx, Types.VARCHAR)
                        .addValue("detail", detailJson, Types.VARCHAR)
                        .addValue("prevHash", prevHash, Types.CHAR)
                        .addValue("hash", hash, Types.CHAR));
    }

    private String lastHash() {
        String hash = jdbc.getJdbcTemplate().query(
                "SELECT hash FROM audit_log ORDER BY id DESC LIMIT 1",
                resultSet -> resultSet.next() ? resultSet.getString(1) : null);
        return hash == null ? AuditHashChain.GENESIS : hash;
    }

    private static String currentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (value != null && value.startsWith("ROLE_")) {
                return value.substring("ROLE_".length());
            }
        }
        return null;
    }

    private static String currentTraceId() {
        String value = MDC.get(TRACE_ID);
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.matches("[A-Za-z0-9._:-]{1," + MAX_TRACE_LENGTH + "}")) {
            return null;
        }
        return trimmed;
    }
}
