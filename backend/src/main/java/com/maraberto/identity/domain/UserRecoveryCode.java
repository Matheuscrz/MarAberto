package com.maraberto.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Código de recuperação de uso único para MFA (TOTP).
 *
 * <p>Armazena apenas o hash SHA-256 do código normalizado; nunca o valor em
 * claro.
 */
@Entity
@Table(name = "user_recovery_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRecoveryCode {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 64, updatable = false)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserRecoveryCode(UUID id, UUID userId, String codeHash, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.codeHash = Objects.requireNonNull(codeHash);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(Instant now) {
        this.usedAt = Objects.requireNonNull(now);
    }
}
