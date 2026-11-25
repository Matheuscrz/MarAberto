package com.maraberto.identity.application;

import com.maraberto.audit.api.AuditActions;
import com.maraberto.audit.api.AuditEvent;
import com.maraberto.audit.api.AuditTrail;
import com.maraberto.identity.api.dto.AdminUserPage;
import com.maraberto.identity.api.dto.AdminUserView;
import com.maraberto.identity.api.dto.CreateAdminUserRequest;
import com.maraberto.identity.api.dto.ProvisionedAdminUserResponse;
import com.maraberto.identity.api.dto.UpdateAdminUserRequest;
import com.maraberto.identity.domain.DuplicateOperationalUserException;
import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.domain.Role;
import com.maraberto.identity.domain.SelfAdminProtectionException;
import com.maraberto.identity.domain.UserNotFoundException;
import com.maraberto.identity.infra.OperationalUserRepository;
import com.maraberto.shared.security.CpfValidator;
import com.maraberto.shared.security.crypto.BlindIndexer;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administração de usuários da instalação (RF20, ADR-027).
 *
 * <p>Não existe exclusão: desativar preserva plantões, ocorrências e a própria
 * auditoria. Criar e resetar geram uma senha temporária devolvida uma única vez,
 * com troca obrigatória no primeiro acesso. Toda alteração entra na trilha.
 */
@Service
public class AdminUserService {

    static final int DEFAULT_LIMIT = 30;
    static final int MAX_LIMIT = 100;
    private static final int MAX_QUERY_LENGTH = 120;

    private final OperationalUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final BlindIndexer blindIndexer;
    private final AuditTrail auditTrail;
    private final NamedParameterJdbcTemplate jdbc;
    private final Duration temporaryPasswordTtl;

    public AdminUserService(
            OperationalUserRepository users,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            TemporaryPasswordGenerator temporaryPasswordGenerator,
            BlindIndexer blindIndexer,
            AuditTrail auditTrail,
            NamedParameterJdbcTemplate jdbc,
            @Value("${maraberto.identity.temporary-password-ttl:PT24H}")
            Duration temporaryPasswordTtl) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.blindIndexer = blindIndexer;
        this.auditTrail = auditTrail;
        this.jdbc = jdbc;
        this.temporaryPasswordTtl = temporaryPasswordTtl;
    }

    /**
     * Página de operadores, do cadastro mais recente para o mais antigo.
     *
     * <p>O termo de busca só aceita igualdade em e-mail/CPF (campos cifrados,
     * com índice cego) ou prefixo de matrícula. Nome não é pesquisável: ele
     * também é cifrado e não pode entrar em {@code LIKE}.
     */
    @Transactional(readOnly = true)
    public AdminUserPage list(
            Boolean active, Role role, String query, int limit, String cursor) {
        int size = clampLimit(limit);
        String term = normalizedQuery(query);
        Long afterSeq = decodeCursor(cursor);

        Specification<OperationalUser> base = filter(active, role, term);
        Specification<OperationalUser> page = afterSeq == null
                ? base
                : base.and((root, criteriaQuery, cb) ->
                        cb.lessThan(root.get("seq"), afterSeq));

        List<OperationalUser> found = users.findAll(
                        page,
                        PageRequest.of(0, size + 1, Sort.by(Sort.Direction.DESC, "seq")))
                .getContent();
        boolean hasMore = found.size() > size;
        List<OperationalUser> pageItems = hasMore ? found.subList(0, size) : found;

        Map<UUID, Instant> lastAccess = lastAccessByUser(
                pageItems.stream().map(OperationalUser::getId).toList());
        List<AdminUserView> items = pageItems.stream()
                .map(user -> AdminUserView.from(user, lastAccess.get(user.getId())))
                .toList();

        String nextCursor = hasMore && !pageItems.isEmpty()
                ? encodeCursor(pageItems.get(pageItems.size() - 1).getSeq())
                : null;
        return new AdminUserPage(items, nextCursor, hasMore, users.count(base));
    }

    @Transactional
    public ProvisionedAdminUserResponse create(
            UUID actorId, CreateAdminUserRequest request, RequestMetadata metadata) {
        String cleanCpf = CpfValidator.normalize(request.cpf());
        if (!CpfValidator.isValid(cleanCpf)) {
            throw new IllegalArgumentException("CPF inválido para cadastro operacional");
        }
        String cpfBidx = blindIndexer.indexDigits(cleanCpf);
        if (users.existsByCpfBidx(cpfBidx)) {
            throw new DuplicateOperationalUserException("CPF já cadastrado no efetivo operacional");
        }
        String emailBidx = blindIndexer.index(request.email());
        if (users.existsByEmailBidx(emailBidx)) {
            throw new DuplicateOperationalUserException("E-mail já cadastrado");
        }
        String badgeNumber = OperationalUser.normalizeBadgeNumber(request.badgeNumber());
        if (badgeNumber != null && users.existsByBadgeNumber(badgeNumber)) {
            throw new DuplicateOperationalUserException("Matrícula já cadastrada");
        }

        String temporaryPassword = temporaryPasswordGenerator.generate();
        String hash = passwordEncoder.encode(temporaryPassword);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(temporaryPasswordTtl);
        OperationalUser user = new OperationalUser(
                UUID.randomUUID(),
                request.name(),
                badgeNumber,
                request.email(),
                emailBidx,
                cleanCpf,
                cpfBidx,
                hash,
                request.role());
        user.assignTemporaryPassword(hash, expiresAt, now);
        users.save(user);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("role", user.getRole().name());
        if (user.getBadgeNumber() != null) {
            detail.put("badgeNumber", user.getBadgeNumber());
        }
        detail.put("mustChangePassword", true);
        auditTrail.record(AuditEvent.of(
                        actorId, AuditActions.USER_CREATED, "USER", user.getId(), detail)
                .withIpAddress(metadata == null ? null : metadata.ipAddress()));

        return new ProvisionedAdminUserResponse(
                AdminUserView.from(user, null), temporaryPassword, expiresAt);
    }

    @Transactional
    public AdminUserView update(
            UUID actorId,
            UUID userId,
            UpdateAdminUserRequest request,
            RequestMetadata metadata) {
        OperationalUser user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (request.role() == null && request.active() == null) {
            throw new IllegalArgumentException("Informe o papel e/ou o estado da conta");
        }

        String ip = metadata == null ? null : metadata.ipAddress();
        if (request.role() != null && request.role() != user.getRole()) {
            protectSelfAdmin(actorId, userId, request.role() == Role.ADMIN);
            Role previous = user.getRole();
            user.changeRole(request.role());
            refreshTokenService.revokeAllForUser(userId);
            auditTrail.record(AuditEvent.of(
                            actorId, AuditActions.USER_ROLE_CHANGED, "USER", userId,
                            Map.of("from", previous.name(), "to", request.role().name()))
                    .withIpAddress(ip));
        }

        if (request.active() != null && request.active() != user.isActive()) {
            protectSelfAdmin(actorId, userId, request.active());
            if (request.active()) {
                user.activate();
            } else {
                user.deactivate();
                refreshTokenService.revokeAllForUser(userId);
            }
            auditTrail.record(AuditEvent.of(
                            actorId,
                            request.active()
                                    ? AuditActions.USER_ACTIVATED
                                    : AuditActions.USER_DEACTIVATED,
                            "USER",
                            userId,
                            Map.of("role", user.getRole().name()))
                    .withIpAddress(ip));
        }

        users.save(user);
        return AdminUserView.from(user, lastAccessOf(userId));
    }

    /**
     * Gera nova senha temporária e revoga todas as sessões.
     *
     * <p>Funciona mesmo com a conta bloqueada por senha esquecida; o operador
     * entra com a temporária e é obrigado a definir a definitiva.
     */
    @Transactional
    public ProvisionedAdminUserResponse resetPassword(
            UUID actorId, UUID userId, RequestMetadata metadata) {
        OperationalUser user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String temporaryPassword = temporaryPasswordGenerator.generate();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(temporaryPasswordTtl);
        user.assignTemporaryPassword(passwordEncoder.encode(temporaryPassword), expiresAt, now);
        users.save(user);
        refreshTokenService.revokeAllForUser(userId);

        auditTrail.record(AuditEvent.of(
                        actorId, AuditActions.USER_PASSWORD_RESET, "USER", userId,
                        Map.of("temporaryPasswordExpiresAt", expiresAt.toString()))
                .withIpAddress(metadata == null ? null : metadata.ipAddress()));

        return new ProvisionedAdminUserResponse(
                AdminUserView.from(user, lastAccessOf(userId)), temporaryPassword, expiresAt);
    }

    private static void protectSelfAdmin(
            UUID actorId, UUID userId, boolean keepsAdmin) {
        if (userId.equals(actorId) && !keepsAdmin) {
            throw new SelfAdminProtectionException();
        }
    }

    private Specification<OperationalUser> filter(
            Boolean active, Role role, String term) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (term != null) {
                if (term.contains("@")) {
                    predicates.add(cb.equal(
                            root.get("emailBidx"), blindIndexer.index(term)));
                } else {
                    String digits = CpfValidator.normalize(term);
                    if (digits.length() == 11) {
                        predicates.add(cb.equal(
                                root.get("cpfBidx"), blindIndexer.indexDigits(digits)));
                    } else {
                        String badge = OperationalUser.normalizeBadgeNumber(term);
                        predicates.add(cb.like(
                                root.get("badgeNumber"), badge + "%"));
                    }
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<UUID, Instant> lastAccessByUser(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Instant> result = new HashMap<>();
        jdbc.query("""
                        SELECT user_id, MAX(last_used_at) AS last_used_at
                          FROM auth_sessions
                         WHERE user_id IN (:userIds)
                         GROUP BY user_id
                        """,
                new MapSqlParameterSource()
                        .addValue("userIds", userIds),
                rs -> {
                    result.put(
                            rs.getObject("user_id", UUID.class),
                            rs.getObject("last_used_at", java.time.OffsetDateTime.class)
                                    .toInstant());
                });
        return result;
    }

    private Instant lastAccessOf(UUID userId) {
        return lastAccessByUser(List.of(userId)).get(userId);
    }

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String normalizedQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.length() <= MAX_QUERY_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_QUERY_LENGTH);
    }

    private static String encodeCursor(Long seq) {
        if (seq == null) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(seq).getBytes(StandardCharsets.UTF_8));
    }

    private static Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(
                    Base64.getUrlDecoder().decode(cursor.trim()),
                    StandardCharsets.UTF_8);
            long seq = Long.parseLong(raw);
            if (seq < 0) {
                throw new IllegalArgumentException("cursor inválido");
            }
            return seq;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("cursor inválido");
        }
    }
}
