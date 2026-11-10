package com.maraberto.identity.application;

import com.maraberto.identity.api.dto.AuthResponse;
import com.maraberto.identity.api.dto.AuthenticatedUserResponse;
import com.maraberto.identity.api.dto.LoginCommand;
import com.maraberto.identity.api.dto.RegisterOperationalUserCommand;
import com.maraberto.identity.domain.AccountInactiveException;
import com.maraberto.identity.domain.DuplicateOperationalUserException;
import com.maraberto.identity.domain.InvalidCredentialsException;
import com.maraberto.identity.domain.InvalidRefreshTokenException;
import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.infra.OperationalUserRepository;
import com.maraberto.identity.infra.security.JwtService;
import com.maraberto.shared.security.CpfValidator;
import com.maraberto.shared.security.crypto.BlindIndexer;
import java.time.Instant;
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
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.blindIndexer = blindIndexer;
        this.refreshTokenService = refreshTokenService;
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

    /** Login único: valida credenciais, abre família de refresh e emite o JWT. */
    @Transactional
    public LoginResult login(LoginCommand cmd, RequestMetadata metadata) {
        OperationalUser user = authenticate(cmd);
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
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeFamily(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse currentUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(OperationalUser::isActive)
                .map(AuthenticatedUserResponse::from)
                .orElseThrow(InvalidRefreshTokenException::new);
    }

    private OperationalUser authenticate(LoginCommand cmd) {
        Optional<OperationalUser> candidate = findByIdentifier(cmd.identifier());

        if (candidate.isEmpty()) {
            // Consome o mesmo custo de Argon2 de uma senha real.
            passwordEncoder.matches(cmd.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        OperationalUser user = candidate.get();
        if (!passwordEncoder.matches(cmd.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new AccountInactiveException();
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
