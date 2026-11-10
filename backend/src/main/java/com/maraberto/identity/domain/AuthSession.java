package com.maraberto.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sessão de refresh de um operador autenticado.
 *
 * <p>O token de refresh é opaco e só existe em claro no cookie
 * {@code HttpOnly}; o banco guarda apenas o hash SHA-256. Cada rotação cria uma
 * nova linha na mesma {@code familyId} e marca a anterior como substituída —
 * assim, a reutilização de um token já rotacionado é detectada e revoga a
 * família inteira (possível roubo de cookie).
 */
@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Família de rotações: todas as sessões derivadas de um mesmo login. */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    /** SHA-256 (base64url) do token opaco; o valor em claro nunca é persistido. */
    @Column(name = "refresh_hash", nullable = false, length = 64, unique = true)
    private String refreshHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    /** Índice cego do IP (nunca o IP em claro) para auditoria. */
    @Column(name = "ip_bidx", length = 64)
    private String ipBlindIndex;

    public AuthSession(
            UUID id,
            UUID userId,
            UUID familyId,
            String refreshHash,
            Instant issuedAt,
            Instant expiresAt,
            String userAgent,
            String ipBlindIndex) {
        this.id = Objects.requireNonNull(id, "ID da sessão não pode ser nulo");
        this.userId = Objects.requireNonNull(userId, "Usuário da sessão não pode ser nulo");
        this.familyId = Objects.requireNonNull(familyId, "Família da sessão não pode ser nula");
        this.refreshHash = Objects.requireNonNull(refreshHash, "Hash do refresh não pode ser nulo");
        this.issuedAt = Objects.requireNonNull(issuedAt, "Emissão da sessão não pode ser nula");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expiração da sessão não pode ser nula");
        this.lastUsedAt = issuedAt;
        this.userAgent = truncate(userAgent);
        this.ipBlindIndex = ipBlindIndex;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean wasRotatedWithin(Instant now, Duration tolerance) {
        return replacedById != null && revokedAt != null && !revokedAt.plus(tolerance).isBefore(now);
    }

    public void markUsed(Instant now) {
        this.lastUsedAt = now;
    }

    public void revoke(Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public void replaceWith(UUID successorId, Instant now) {
        this.revokedAt = now;
        this.replacedById = successorId;
        this.lastUsedAt = now;
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String trimmed = userAgent.trim();
        return trimmed.length() <= 256 ? trimmed : trimmed.substring(0, 256);
    }
}
