package com.maraberto.identity.domain;

import com.maraberto.shared.security.CpfValidator;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
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

    @Convert(converter = OperationalUserFieldConverters.NameConverter.class)
    @Column(nullable = false, length = 512)
    private String name;

    /** Matrícula funcional (ex.: {@code BM-10492}); opcional. */
    @Column(name = "badge_number", length = 32)
    private String badgeNumber;

    @Convert(converter = OperationalUserFieldConverters.EmailConverter.class)
    @Column(nullable = false, length = 512)
    private String email;

    @Column(name = "email_bidx", nullable = false, length = 64, unique = true)
    private String emailBidx;

    @Convert(converter = OperationalUserFieldConverters.CpfConverter.class)
    @Column(nullable = false, length = 512)
    private String cpf;

    @Column(name = "cpf_bidx", nullable = false, length = 64, unique = true)
    private String cpfBidx;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Convert(converter = OperationalUserFieldConverters.TotpSecretConverter.class)
    @Column(name = "totp_secret", length = 512)
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    @Column(name = "totp_enrolled_at")
    private Instant totpEnrolledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    /**
     * Senha temporária ainda não trocada. Enquanto for {@code true}, o access
     * token leva a marca e o filtro de segurança só libera a troca de senha,
     * o próprio perfil e o logout.
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /** Fim da validade da senha temporária; nulo quando a senha é definitiva. */
    @Column(name = "temporary_password_expires_at")
    private Instant temporaryPasswordExpiresAt;

    /** Última troca feita pelo próprio operador. */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /**
     * Sequência de criação, usada como chave estável da paginação da TI.
     * Preenchida pelo banco; a aplicação nunca escreve nela.
     */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    /**
     * Tentativas de senha errada desde o último login bem-sucedido. Cresce até
     * virar bloqueio; um acerto zera. Nunca é exposto na API.
     */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    /**
     * Fim do bloqueio por força bruta. {@code null} ou instante passado
     * significam conta liberada — não há job para "desbloquear".
     */
    @Column(name = "locked_until")
    private Instant lockedUntil;

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
        this.mustChangePassword = false;
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

    public void changeRole(Role role) {
        this.role = Objects.requireNonNull(role, "Perfil não pode ser nulo");
        this.updatedAt = Instant.now();
    }

    /**
     * Aplica uma senha temporária gerada pela TI.
     *
     * <p>A conta perde bloqueios anteriores (o reset existe justamente para quem
     * esqueceu a senha) e precisa trocá-la no primeiro acesso.
     */
    public void assignTemporaryPassword(String passwordHash, Instant expiresAt, Instant now) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "Hash de senha não pode ser nulo");
        this.mustChangePassword = true;
        this.temporaryPasswordExpiresAt = Objects.requireNonNull(
                expiresAt, "Expiração da senha temporária não pode ser nula");
        this.updatedAt = now;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /** Exige troca de senha no primeiro acesso (usado no bootstrap inicial, sem expiração). */
    public void requirePasswordChangeOnFirstLogin(Instant now) {
        this.mustChangePassword = true;
        this.temporaryPasswordExpiresAt = null;
        this.updatedAt = now;
    }

    /** Inicia o pareamento TOTP salvando um segredo pendente (ainda desativado). */
    public void stageTotpSecret(String base32Secret, Instant now) {
        this.totpSecret = Objects.requireNonNull(base32Secret, "Segredo TOTP não pode ser nulo");
        this.totpEnabled = false;
        this.totpEnrolledAt = null;
        this.updatedAt = now;
    }

    /** Confirma e ativa o MFA TOTP após validação do primeiro código. */
    public void enableTotp(Instant now) {
        if (this.totpSecret == null || this.totpSecret.isBlank()) {
            throw new IllegalStateException("Inicie a configuração do autenticador antes de confirmar");
        }
        this.totpEnabled = true;
        this.totpEnrolledAt = now;
        this.updatedAt = now;
    }

    /** Desativa o MFA TOTP e remove o segredo armazenado. */
    public void disableTotp(Instant now) {
        this.totpSecret = null;
        this.totpEnabled = false;
        this.totpEnrolledAt = null;
        this.updatedAt = now;
    }

    /** Define a senha definitiva e encerra a validade da temporária. */
    public void definePassword(String passwordHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "Hash de senha não pode ser nulo");
        this.mustChangePassword = false;
        this.temporaryPasswordExpiresAt = null;
        this.passwordChangedAt = now;
        this.updatedAt = now;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /** Temporária vencida: o login deve orientar a TI a gerar outra. */
    public boolean isTemporaryPasswordExpired(Instant now) {
        return mustChangePassword
                && temporaryPasswordExpiresAt != null
                && !temporaryPasswordExpiresAt.isAfter(now);
    }

    /** A conta só está bloqueada dentro da janela gravada; o passado libera. */
    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void resetFailedLogins() {
        if (failedLoginAttempts != 0 || lockedUntil != null) {
            this.failedLoginAttempts = 0;
            this.lockedUntil = null;
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Registra uma senha errada e calcula o bloqueio progressivo.
     *
     * <p>Antes do limiar a conta continua tentável (o operador pode ter só
     * errado a senha). A partir dele, cada tentativa dobra a espera até o teto:
     * o atacante paga caro e o dono da conta volta em minutos, não em horas.
     *
     * @return instante em que a conta volta a aceitar senha
     */
    public Instant registerFailedLogin(
            Instant now,
            int maxAttempts,
            Duration baseLock,
            Duration maxLock) {
        this.failedLoginAttempts = failedLoginAttempts + 1;
        this.updatedAt = now;
        if (failedLoginAttempts < maxAttempts) {
            this.lockedUntil = null;
            return now;
        }
        int expoent = failedLoginAttempts - maxAttempts;
        long baseMillis = baseLock.toMillis();
        long maxMillis = maxLock.toMillis();
        long multiplier = 1L << Math.min(expoent, 20);
        long lockMillis = Math.min(maxMillis, baseMillis * multiplier);
        this.lockedUntil = now.plusMillis(Math.max(baseMillis, lockMillis));
        return this.lockedUntil;
    }
}
