package com.maraberto.identity.infra.security;

import com.maraberto.identity.api.AccessTokenClaims;
import com.maraberto.identity.api.AccessTokenVerifier;
import com.maraberto.identity.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Valida o JWT reaproveitando o {@link JwtService} do login.
 */
@Component
public class JwtAccessTokenVerifier implements AccessTokenVerifier {

    private final JwtService jwtService;

    public JwtAccessTokenVerifier(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Optional<AccessTokenClaims> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = jwtService.validateAndExtractClaims(token);
            UUID userId = UUID.fromString(claims.getSubject());
            Role role = Role.valueOf(claims.get("role", String.class));
            return Optional.of(new AccessTokenClaims(userId, role.name()));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
