package com.maraberto.identity.domain;

import com.maraberto.shared.security.CpfValidator;
import com.maraberto.shared.security.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Operador do efetivo (guarda-vidas, coordenação ou TI).
 *
 * <p>Nome, e-mail e CPF são cifrados no banco ({@link EncryptedStringConverter})
 * e pesquisados por índice cego. A matrícula é um identificador funcional:
 * fica em claro para login e exibição, mas nunca é usada como segredo.
 */
@Entity
@Table(name = "operational_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperationalUser {

    @Id
    private UUID id;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 512)
    private String name;

    /** Matrícula funcional (ex.: {@code BM-10492}); opcional. */
    @Column(name = "badge_number", length = 32)
    private String badgeNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 512)
    private String email;

    @Column(name = "email_bidx", nullable = false, length = 64, unique = true)
    private String emailBidx;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 512)
    private String cpf;

    @Column(name = "cpf_bidx", nullable = false, length = 64, unique = true)
    private String cpfBidx;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OperationalUser(
            UUID id,
            String name,
            String badgeNumber,
            String email,
            String emailBidx,
            String cpf,
            String cpfBidx,
            String passwordHash,
            Role role) {
        if (!CpfValidator.isValid(cpf)) {
            throw new IllegalArgumentException("CPF operacional inválido");
        }
        this.id = Objects.requireNonNull(id, "ID não pode ser nulo");
        this.name = Objects.requireNonNull(name, "Nome não pode ser nulo");
        this.badgeNumber = normalizeBadgeNumber(badgeNumber);
        this.email = Objects.requireNonNull(email, "E-mail não pode ser nulo");
        this.emailBidx = Objects.requireNonNull(emailBidx, "Blind index de e-mail não pode ser nulo");
        this.cpf = cpf;
        this.cpfBidx = Objects.requireNonNull(cpfBidx, "Blind index de CPF não pode ser nulo");
        this.passwordHash = Objects.requireNonNull(passwordHash, "Hash de senha não pode ser nulo");
        this.role = Objects.requireNonNull(role, "Perfil não pode ser nulo");
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Matrícula normalizada para comparação (maiúsculas, sem espaços nas pontas). */
    public static String normalizeBadgeNumber(String badgeNumber) {
        if (badgeNumber == null || badgeNumber.isBlank()) {
            return null;
        }
        return badgeNumber.trim().toUpperCase(Locale.ROOT);
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "Hash de senha não pode ser nulo");
        this.updatedAt = Instant.now();
    }
}
