package com.maraberto.identity.application;

import com.maraberto.audit.api.AuditActions;
import com.maraberto.audit.api.AuditEvent;
import com.maraberto.audit.api.AuditTrail;
import com.maraberto.identity.api.dto.MfaConfirmResponse;
import com.maraberto.identity.api.dto.MfaSetupResponse;
import com.maraberto.identity.domain.InvalidCredentialsException;
import com.maraberto.identity.domain.InvalidRefreshTokenException;
import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.domain.Role;
import com.maraberto.identity.domain.UserRecoveryCode;
import com.maraberto.identity.infra.OperationalUserRepository;
import com.maraberto.identity.infra.UserRecoveryCodeRepository;
import com.maraberto.identity.infra.security.TokenRevocationService;
import com.maraberto.identity.infra.security.TotpGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de segundo fator (TOTP RFC 6238) e códigos de recuperação de uso único
 * para perfis privilegiados ({@code COORDINATOR} e {@code ADMIN}).
 */
@Service
public class MfaService {

    private static final String ISSUER = "MarAberto";
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final OperationalUserRepository users;
    private final UserRecoveryCodeRepository recoveryCodes;
    private final TotpGenerator totpGenerator;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final AuditTrail auditTrail;
    private final SecureRandom random = new SecureRandom();

    public MfaService(
            OperationalUserRepository users,
            UserRecoveryCodeRepository recoveryCodes,
            TotpGenerator totpGenerator,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            TokenRevocationService tokenRevocationService,
            AuditTrail auditTrail) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.totpGenerator = totpGenerator;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.tokenRevocationService = tokenRevocationService;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public MfaSetupResponse setup(UUID userId) {
        OperationalUser user = requirePrivilegedActiveUser(userId);
        String secret = totpGenerator.generateSecret();
        user.stageTotpSecret(secret, Instant.now());
        users.save(user);
        String accountLabel = user.getBadgeNumber() != null ? user.getBadgeNumber() : user.getEmail();
        String uri = totpGenerator.buildOtpAuthUri(ISSUER, accountLabel, secret);
        return new MfaSetupResponse(secret, uri);
    }

    @Transactional
    public MfaConfirmResponse confirm(UUID userId, String code, RequestMetadata metadata) {
        OperationalUser user = requirePrivilegedActiveUser(userId);
        Instant now = Instant.now();
        if (user.getTotpSecret() == null || !totpGenerator.verifyCode(user.getTotpSecret(), code, now)) {
            throw new InvalidCredentialsException();
        }
        user.enableTotp(now);
        users.save(user);

        recoveryCodes.deleteByUserId(userId);
        List<String> plainCodes = generateRecoveryCodes();
        List<UserRecoveryCode> entities = new ArrayList<>(plainCodes.size());
        for (String plain : plainCodes) {
            entities.add(new UserRecoveryCode(UUID.randomUUID(), userId, hashRecoveryCode(plain), now));
        }
        recoveryCodes.saveAll(entities);

        auditTrail.record(AuditEvent.of(
                        userId, AuditActions.MFA_ENABLED, "USER", userId, Map.of("role", user.getRole().name()))
                .withIpAddress(metadata == null ? null : metadata.ipAddress()));

        return new MfaConfirmResponse(plainCodes);
    }

    @Transactional
    public void disable(UUID userId, String currentPassword, String code, RequestMetadata metadata) {
        OperationalUser user = requirePrivilegedActiveUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!verifyChallengeCode(user, code)) {
            throw new InvalidCredentialsException();
        }
        Instant now = Instant.now();
        user.disableTotp(now);
        users.save(user);
        recoveryCodes.deleteByUserId(userId);
        refreshTokenService.revokeAllForUser(userId);
        tokenRevocationService.revokeAllForUser(userId, now);

        auditTrail.record(AuditEvent.of(
                        userId, AuditActions.MFA_DISABLED, "USER", userId, Map.of("role", user.getRole().name()))
                .withIpAddress(metadata == null ? null : metadata.ipAddress()));
    }

    /**
     * Valida o código TOTP atual ou consome um código de recuperação de uso único.
     */
    @Transactional
    public boolean verifyChallengeCode(OperationalUser user, String rawCode) {
        if (!user.isTotpEnabled() || rawCode == null || rawCode.isBlank()) {
            return false;
        }
        Instant now = Instant.now();
        if (totpGenerator.verifyCode(user.getTotpSecret(), rawCode, now)) {
            return true;
        }
        String hash = hashRecoveryCode(rawCode);
        return recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), hash)
                .map(code -> {
                    code.markUsed(now);
                    recoveryCodes.save(code);
                    return true;
                })
                .orElse(false);
    }

    private OperationalUser requirePrivilegedActiveUser(UUID userId) {
        OperationalUser user = users.findById(userId)
                .filter(OperationalUser::isActive)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.COORDINATOR) {
            throw new IllegalArgumentException("MFA TOTP está disponível para Coordenação e TI");
        }
        return user;
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(9);
            for (int j = 0; j < 8; j++) {
                if (j == 4) {
                    sb.append('-');
                }
                sb.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    static String hashRecoveryCode(String rawCode) {
        String normalized = rawCode.trim().replace("-", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
