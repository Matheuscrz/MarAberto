package com.maraberto.identity.application;

import com.maraberto.audit.api.AuditActions;
import com.maraberto.audit.api.AuditEntityTypes;
import com.maraberto.audit.api.AuditEvent;
import com.maraberto.audit.api.AuditTrail;
import com.maraberto.identity.api.dto.UserBeachScopesView;
import com.maraberto.identity.domain.UserNotFoundException;
import com.maraberto.identity.infra.OperationalUserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gerenciamento do escopo territorial ({@code user_beach_scopes}) pela TI (F-06).
 *
 * <p>Coordenadores sem praias vinculadas mantêm escopo irrestrito; quando há
 * vínculos, consultas de ocorrências, equipe e triagem são filtradas para as
 * praias atribuídas.
 */
@Service
public class UserBeachScopeService {

    private final OperationalUserRepository users;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditTrail auditTrail;

    public UserBeachScopeService(
            OperationalUserRepository users,
            NamedParameterJdbcTemplate jdbc,
            AuditTrail auditTrail) {
        this.users = users;
        this.jdbc = jdbc;
        this.auditTrail = auditTrail;
    }

    @Transactional(readOnly = true)
    public UserBeachScopesView getScopes(UUID userId) {
        requireUserExists(userId);
        List<UUID> beachIds = jdbc.query(
                "SELECT beach_id FROM user_beach_scopes WHERE user_id = :userId ORDER BY beach_id",
                new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> rs.getObject("beach_id", UUID.class));
        return new UserBeachScopesView(userId, List.copyOf(beachIds));
    }

    @Transactional
    public UserBeachScopesView replaceScopes(UUID actorId, UUID userId, List<UUID> requestedBeachIds) {
        requireUserExists(userId);
        Set<UUID> distinct = new LinkedHashSet<>(requestedBeachIds);
        if (!distinct.isEmpty()) {
            Long existingCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM beaches WHERE id IN (:ids)",
                    new MapSqlParameterSource("ids", distinct),
                    Long.class);
            if (existingCount == null || existingCount != distinct.size()) {
                throw new IllegalArgumentException(
                        "Uma ou mais áreas de banho informadas não existem.");
            }
        }
        jdbc.update(
                "DELETE FROM user_beach_scopes WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId));
        for (UUID beachId : distinct) {
            jdbc.update("""
                            INSERT INTO user_beach_scopes (user_id, beach_id)
                            VALUES (:userId, :beachId)
                            ON CONFLICT (user_id, beach_id) DO NOTHING
                            """,
                    new MapSqlParameterSource()
                            .addValue("userId", userId)
                            .addValue("beachId", beachId));
        }
        auditTrail.record(AuditEvent.of(
                actorId,
                AuditActions.USER_SCOPE_UPDATED,
                AuditEntityTypes.USER,
                userId,
                Map.of("beachCount", distinct.size())));
        return new UserBeachScopesView(userId, List.copyOf(distinct));
    }

    private void requireUserExists(UUID userId) {
        if (!users.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
    }
}
