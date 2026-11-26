package com.maraberto.identity.application;

import com.maraberto.audit.api.AuditActions;
import com.maraberto.audit.api.AuditEvent;
import com.maraberto.audit.api.AuditTrail;
import com.maraberto.identity.api.dto.ActiveSessionView;
import com.maraberto.identity.api.dto.AuthResponse;
import com.maraberto.identity.api.dto.AuthenticatedUserResponse;
import com.maraberto.identity.api.dto.LoginCommand;
import com.maraberto.identity.api.dto.RegisterOperationalUserCommand;
import com.maraberto.identity.domain.AccountInactiveException;
import com.maraberto.identity.domain.DuplicateOperationalUserException;
import com.maraberto.identity.domain.InvalidCredentialsException;
import com.maraberto.identity.domain.InvalidRefreshTokenException;
import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.domain.TemporaryPasswordExpiredException;
import com.maraberto.identity.infra.OperationalUserRepository;
import com.maraberto.identity.infra.security.JwtService;
import com.maraberto.identity.infra.security.LoginProtectionProperties;
import com.maraberto.identity.infra.security.TokenRevocationService;
import com.maraberto.shared.security.CpfValidator;
import com.maraberto.shared.security.crypto.BlindIndexer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de identidade: cadastro do efetivo, login, refresh, logout e
 * leitura do operador autenticado.
 *
 * <p>O identificador de login pode ser e-mail, CPF ou matrícula. A busca em
 * e-mail/CPF usa índice cego (os campos são cifrados); a senha é verificada com
 * Argon2id. Credencial inexistente e senha errada devolvem o mesmo erro.
 */
@Service
public class AuthenticationService {

    private final OperationalUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BlindIndexer blindIndexer;
    private final RefreshTokenService refreshTokenService;
    private final LoginProtectionProperties loginProtection;
    private final LoginIpRateLimiter ipRateLimiter;
    private final PasswordPolicy passwordPolicy;
    private final AuditTrail auditTrail;
    private final TokenRevocationService tokenRevocationService;
    private final MfaService mfaService;

    /**
     * Hash descartável usado para gastar o mesmo tempo de Argon2 quando o
     * identificador não existe (evita enumeração por tempo de resposta).
     */
    private final String dummyPasswordHash;

    public AuthenticationService(
            OperationalUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            BlindIndexer blindIndexer,
            RefreshTokenService refreshTokenService,
            LoginProtectionProperties loginProtection,
            LoginIpRateLimiter ipRateLimiter,
            PasswordPolicy passwordPolicy,
            AuditTrail auditTrail,
            TokenRevocationService tokenRevocationService,
            MfaService mfaService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.blindIndexer = blindIndexer;
        this.refreshTokenService = refreshTokenService;
        this.loginProtection = loginProtection;
        this.ipRateLimiter = ipRateLimiter;
        this.passwordPolicy = passwordPolicy;
        this.auditTrail = auditTrail;
        this.tokenRevocationService = tokenRevocationService;
        this.mfaService = mfaService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UUID registerOperational(RegisterOperationalUserCommand cmd) {
        String cleanCpf = CpfValidator.normalize(cmd.cpf());
        if (!CpfValidator.isValid(cleanCpf)) {
            throw new IllegalArgumentException("CPF inválido para cadastro operacional");
        }

        String cpfBidx = blindIndexer.indexDigits(cleanCpf);
        if (userRepository.existsByCpfBidx(cpfBidx)) {
            throw new DuplicateOperationalUserException("CPF já cadastrado no efetivo operacional");
        }

        String emailBidx = blindIndexer.index(cmd.email());
        if (userRepository.existsByEmailBidx(emailBidx)) {
            throw new DuplicateOperationalUserException("E-mail já cadastrado");
        }

        String badgeNumber = OperationalUser.normalizeBadgeNumber(cmd.badgeNumber());
        if (badgeNumber != null && userRepository.existsByBadgeNumber(badgeNumber)) {
            throw new DuplicateOperationalUserException("Matrícula já cadastrada");
        }

        OperationalUser user = new OperationalUser(
                UUID.randomUUID(),
                cmd.name(),
                badgeNumber,
                cmd.email(),
                emailBidx,
                cleanCpf,
                cpfBidx,
                passwordEncoder.encode(cmd.password()),
                cmd.role());

        userRepository.save(user);
        return user.getId();
    }

    /**
     * Login único: valida credenciais, abre família de refresh e emite o JWT.
     *
     * <p>Deliberadamente **sem** {@code @Transactional}: a falha de senha
     * precisa gravar o contador de bloqueio e só então virar 401. Dentro de uma
     * transação, a exceção faria rollback e o bloqueio nunca existiria.
     */
    public LoginResult login(LoginCommand cmd, RequestMetadata metadata) {
        ipRateLimiter.check(metadata.ipAddress());
        OperationalUser user = authenticate(cmd);
        if (user.isTotpEnabled()) {
            String challenge = jwtService.generateMfaChallengeToken(user.getId());
            return new LoginResult(
                    AuthResponse.mfaChallenge(challenge, AuthenticatedUserResponse.from(user)),
                    null,
                    null);
        }
        RefreshTokenService.IssuedRefreshToken refresh =
                refreshTokenService.issueForNewLogin(user, metadata);
        return new LoginResult(
                response(user), refresh.token(), refresh.expiresAt());
    }

    /**
     * Conclui o login 2FA após validação do código TOTP ou código de recuperação.
     */
    public LoginResult verifyMfa(String challengeToken, String code, RequestMetadata metadata) {
        ipRateLimiter.check(metadata.ipAddress());
        UUID userId;
        try {
            userId = jwtService.validateMfaChallengeToken(challengeToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidCredentialsException();
        }
        OperationalUser user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive()) {
            throw new AccountInactiveException();
        }
        if (!mfaService.verifyChallengeCode(user, code)) {
            throw new InvalidCredentialsException();
        }
        RefreshTokenService.IssuedRefreshToken refresh =
                refreshTokenService.issueForNewLogin(user, metadata);
        return new LoginResult(
                response(user), refresh.token(), refresh.expiresAt());
    }

    /**
     * Troca o refresh opaco por um novo par access + refresh (com rotação).
     *
     * <p>Deliberadamente **sem** {@code @Transactional}: a revogação por reuso
     * precisa commitar antes de a exceção virar 401, senão o rollback devolveria
     * a família revogada à vida.
     */
    public LoginResult refresh(String rawRefreshToken, RequestMetadata metadata) {
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService
                .rotate(rawRefreshToken, metadata)
                .orElseThrow(InvalidRefreshTokenException::new);
        return new LoginResult(
                response(rotated.user()), rotated.token(), rotated.expiresAt());
    }

    @Transactional
    public void logout(String rawRefreshToken, String bearerToken) {
        refreshTokenService.revokeFamily(rawRefreshToken);
        if (bearerToken != null && !bearerToken.isBlank()) {
            try {
                Claims claims = jwtService.validateAndExtractClaims(bearerToken.trim());
                Date exp = claims.getExpiration();
                tokenRevocationService.revokeToken(
                        claims.getId(), exp == null ? null : exp.toInstant());
            } catch (JwtException | IllegalArgumentException ignored) {
                // Token já expirado ou inválido não precisa entrar na blacklist.
            }
        }
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse currentUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(OperationalUser::isActive)
                .map(AuthenticatedUserResponse::from)
                .orElseThrow(InvalidRefreshTokenException::new);
    }

    @Transactional(readOnly = true)
    public List<ActiveSessionView> activeSessions(UUID userId, String rawCurrentRefreshToken) {
        return refreshTokenService.listActiveSessions(userId, rawCurrentRefreshToken);
    }

    @Transactional
    public boolean revokeSession(UUID userId, UUID sessionFamilyId, String rawCurrentRefreshToken, String bearerToken) {
        boolean revokedCurrent = refreshTokenService.revokeDeviceSession(userId, sessionFamilyId, rawCurrentRefreshToken);
        if (revokedCurrent) {
            revokeBearerIfPresent(bearerToken);
        }
        return revokedCurrent;
    }

    @Transactional
    public void revokeOtherSessions(UUID userId, String rawCurrentRefreshToken) {
        refreshTokenService.revokeOtherDeviceSessions(userId, rawCurrentRefreshToken);
    }

    /**
     * Troca a senha do operador autenticado (inclusive a temporária).
     *
     * <p>Revoga todas as sessões: quem tinha o refresh antigo não continua
     * dentro. O controller limpa o cookie e o cliente volta ao login — é mais
     * seguro do que tentar preservar a sessão que acabou de mudar de segredo.
     */
    @Transactional
    public void changePassword(
            UUID userId,
            String currentPassword,
            String newPassword,
            RequestMetadata metadata) {
        changePassword(userId, currentPassword, newPassword, metadata, null);
    }

    @Transactional
    public void changePassword(
            UUID userId,
            String currentPassword,
            String newPassword,
            RequestMetadata metadata,
            String bearerToken) {
        OperationalUser user = userRepository.findById(userId)
                .filter(OperationalUser::isActive)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        Instant now = Instant.now();
        if (user.isTemporaryPasswordExpired(now)) {
            throw new TemporaryPasswordExpiredException();
        }
        passwordPolicy.validate(newPassword, currentPassword);

        user.definePassword(passwordEncoder.encode(newPassword), now);
        userRepository.saveAndFlush(user);
        refreshTokenService.revokeAllForUser(userId);
        tokenRevocationService.revokeAllForUser(userId, now);
        revokeBearerIfPresent(bearerToken);

        auditTrail.record(AuditEvent.of(
                        userId, AuditActions.PASSWORD_CHANGED, "USER", userId, Map.of())
                .withIpAddress(metadata == null ? null : metadata.ipAddress()));
    }

    private void revokeBearerIfPresent(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return;
        }
        try {
            Claims claims = jwtService.validateAndExtractClaims(bearerToken.trim());
            Date exp = claims.getExpiration();
            tokenRevocationService.revokeToken(
                    claims.getId(), exp == null ? null : exp.toInstant());
        } catch (JwtException | IllegalArgumentException ignored) {
            // Token já expirado ou inválido não precisa entrar na blacklist.
        }
    }

    private OperationalUser authenticate(LoginCommand cmd) {
        Optional<OperationalUser> candidate = findByIdentifier(cmd.identifier());

        if (candidate.isEmpty()) {
            // Consome o mesmo custo de Argon2 de uma senha real.
            passwordEncoder.matches(cmd.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        OperationalUser user = candidate.get();
        Instant now = Instant.now();

        if (user.isLockedAt(now)) {
            // Mantém o custo de verificação e a resposta uniforme: o atacante
            // não descobre que a conta está bloqueada nem se a senha estava certa.
            passwordEncoder.matches(cmd.password(), user.getPasswordHash());
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(cmd.password(), user.getPasswordHash())) {
            user.registerFailedLogin(
                    now,
                    loginProtection.getAccountMaxAttempts(),
                    loginProtection.getAccountBaseLock(),
                    loginProtection.getAccountMaxLock());
            userRepository.saveAndFlush(user);
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            throw new AccountInactiveException();
        }

        if (user.isTemporaryPasswordExpired(now)) {
            throw new TemporaryPasswordExpiredException();
        }

        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.resetFailedLogins();
            userRepository.saveAndFlush(user);
        }
        return user;
    }

    private Optional<OperationalUser> findByIdentifier(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.indexOf('@') > 0) {
            return userRepository.findByEmailBidx(blindIndexer.index(value));
        }

        String digits = CpfValidator.normalize(value);
        if (digits.length() == 11) {
            return userRepository.findByCpfBidx(blindIndexer.indexDigits(digits));
        }

        String badgeNumber = OperationalUser.normalizeBadgeNumber(value);
        if (badgeNumber == null) {
            return Optional.empty();
        }
        return userRepository.findByBadgeNumber(badgeNumber);
    }

    private AuthResponse response(OperationalUser user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                "Bearer",
                jwtService.accessTokenTtl().toSeconds(),
                AuthenticatedUserResponse.from(user));
    }

    /**
     * Resultado interno do login/refresh: a resposta HTTP e o valor do refresh
     * que o controller coloca no cookie {@code HttpOnly}.
     */
    public record LoginResult(AuthResponse response, String refreshToken, Instant refreshExpiresAt) {
    }
}
