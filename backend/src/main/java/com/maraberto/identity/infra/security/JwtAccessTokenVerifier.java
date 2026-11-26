package com.maraberto.identity.infra.security;

import com.maraberto.identity.api.AccessTokenClaims;
import com.maraberto.identity.api.AccessTokenVerifier;
import com.maraberto.identity.domain.Role;
import com.maraberto.identity.infra.OperationalUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Valida o JWT reaproveitando o {@link JwtService} do login e aplicando as
 * mesmas travas do filtro HTTP: rejeita desafio MFA, verifica blacklist de
 * revogação e revalida {@code active}/papel atual do usuário no banco.
 */
@Component
public class JwtAccessTokenVerifier implements AccessTokenVerifier {

    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;
    private final OperationalUserRepository userRepository;

    public JwtAccessTokenVerifier(JwtService jwtService) {
        this(jwtService, null, null);
    }

    @Autowired
    public JwtAccessTokenVerifier(
            JwtService jwtService,
            TokenRevocationService tokenRevocationService,
            OperationalUserRepository userRepository) {
        this.jwtService = jwtService;
        this.tokenRevocationService = tokenRevocationService;
        this.userRepository = userRepository;
    }

    @Override
    public Optional<AccessTokenClaims> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = jwtService.validateAndExtractClaims(token);
            if (JwtService.PURPOSE_MFA_CHALLENGE.equals(claims.get("purpose", String.class))) {
                return Optional.empty();
            }
            String subject = claims.getSubject();
            String roleClaim = claims.get("role", String.class);
            if (subject == null || subject.isBlank() || roleClaim == null || roleClaim.isBlank()) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(subject);
            Role role = Role.valueOf(roleClaim);
            if (tokenRevocationService != null) {
                Date issuedAtDate = claims.getIssuedAt();
                Instant issuedAt = issuedAtDate == null ? null : issuedAtDate.toInstant();
                if (tokenRevocationService.isRevoked(claims.getId(), userId, issuedAt)) {
                    return Optional.empty();
                }
            }
            if (userRepository != null) {
                Optional<Role> activeRole = userRepository.findActiveRoleById(userId);
                if (activeRole.isEmpty() || activeRole.get() != role) {
                    return Optional.empty();
                }
            }
            boolean mustChangePassword = Boolean.TRUE.equals(
                    claims.get("mustChangePassword", Boolean.class));
            return Optional.of(new AccessTokenClaims(userId, role.name(), mustChangePassword));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
