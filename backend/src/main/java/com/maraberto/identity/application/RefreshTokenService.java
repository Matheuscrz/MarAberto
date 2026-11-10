package com.maraberto.identity.application;

import com.maraberto.identity.domain.AuthSession;
import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.infra.AuthSessionRepository;
import com.maraberto.identity.infra.OperationalUserRepository;
import com.maraberto.identity.infra.security.JwtProperties;
import com.maraberto.shared.security.crypto.BlindIndexer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emissão, rotação e revogação do refresh token opaco.
 *
 * <p>Regras (ADR-009):
 * <ul>
 *   <li>o token em claro só existe no cookie {@code HttpOnly}; o banco guarda o
 *       SHA-256;</li>
 *   <li>cada login abre uma família; cada refresh rotaciona o token dentro dela;</li>
 *   <li>token revogado apresentado fora da janela de tolerância é tratado como
 *       reutilização e revoga a família inteira;</li>
 *   <li>conta desativada perde todas as sessões.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthSessionRepository sessions;
    private final OperationalUserRepository users;
    private final JwtProperties properties;
    private final BlindIndexer blindIndexer;

    public RefreshTokenService(
            AuthSessionRepository sessions,
            OperationalUserRepository users,
            JwtProperties properties,
            BlindIndexer blindIndexer) {
        this.sessions = sessions;
        this.users = users;
        this.properties = properties;
        this.blindIndexer = blindIndexer;
    }

    /** Abre uma nova família de refresh para um login recém-autenticado. */
    @Transactional
    public IssuedRefreshToken issueForNewLogin(OperationalUser user, RequestMetadata metadata) {
        CreatedSession created = createSession(UUID.randomUUID(), user, metadata);
        return new IssuedRefreshToken(created.rawToken(), created.session().getExpiresAt());
    }

    /**
     * Rotaciona o refresh token.
     *
     * <p>Devolve vazio quando a sessão não é mais válida. A revogação da família
     * (reuso, expiração ou conta inativa) precisa **commitar**, por isso este
     * método não lança exceção: quem lança é o chamador, fora da transação.
     */
    @Transactional
    public Optional<RotatedRefreshToken> rotate(String rawToken, RequestMetadata metadata) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        Optional<AuthSession> found = sessions.findByRefreshHashForUpdate(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AuthSession session = found.get();

        if (session.isExpired(now)) {
            sessions.revokeFamily(session.getFamilyId(), now);
            return Optional.empty();
        }

        if (session.isRevoked()) {
            if (session.wasRotatedWithin(now, properties.getRefreshReuseTolerance())) {
                // Duas abas renovaram quase juntas: emite um irmão na mesma
                // família em vez de punir o usuário. O cookie é compartilhado,
                // então o navegador fica com o token mais recente.
                return activeUser(session.getUserId(), now)
                        .map(user -> createSession(session.getFamilyId(), user, metadata))
                        .map(created -> new RotatedRefreshToken(
                                created.user(), created.rawToken(), created.session().getExpiresAt()));
            }
            sessions.revokeFamily(session.getFamilyId(), now);
            return Optional.empty();
        }

        Optional<OperationalUser> active = activeUser(session.getUserId(), now);
        if (active.isEmpty()) {
            return Optional.empty();
        }

        OperationalUser user = active.get();
        CreatedSession successor = createSession(session.getFamilyId(), user, metadata);
        session.replaceWith(successor.session().getId(), now);
        return Optional.of(new RotatedRefreshToken(
                user, successor.rawToken(), successor.session().getExpiresAt()));
    }

    /** Revoga a família do token apresentado (logout). Idempotente. */
    @Transactional
    public void revokeFamily(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessions.findByRefreshHashForUpdate(hash(rawToken))
                .ifPresent(session -> sessions.revokeFamily(session.getFamilyId(), Instant.now()));
    }

    /** Revoga todas as sessões do operador (desativação, troca de senha). */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        sessions.revokeAllForUser(userId, Instant.now());
    }

    public long refreshTokenTtlSeconds() {
        return properties.getRefreshTokenTtl().toSeconds();
    }

    private Optional<OperationalUser> activeUser(UUID userId, Instant now) {
        Optional<OperationalUser> user = users.findById(userId);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        if (!user.get().isActive()) {
            sessions.revokeAllForUser(userId, now);
            return Optional.empty();
        }
        return user;
    }

    private CreatedSession createSession(UUID familyId, OperationalUser user, RequestMetadata metadata) {
        Instant now = Instant.now();
        String rawToken = newToken();
        AuthSession session = new AuthSession(
                UUID.randomUUID(),
                user.getId(),
                familyId,
                hash(rawToken),
                now,
                now.plus(properties.getRefreshTokenTtl()),
                metadata == null ? null : metadata.userAgent(),
                blindIndex(metadata == null ? null : metadata.ipAddress()));
        sessions.save(session);
        return new CreatedSession(user, session, rawToken);
    }

    private String blindIndex(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        return blindIndexer.index(ipAddress);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return ENCODER.encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private record CreatedSession(OperationalUser user, AuthSession session, String rawToken) {
    }

    public record IssuedRefreshToken(String token, Instant expiresAt) {
    }

    public record RotatedRefreshToken(OperationalUser user, String token, Instant expiresAt) {
    }
}
