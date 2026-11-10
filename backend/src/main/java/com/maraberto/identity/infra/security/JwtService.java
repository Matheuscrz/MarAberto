package com.maraberto.identity.infra.security;

import com.maraberto.identity.domain.OperationalUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/**
 * Emissão e validação do access token (JWT HS256).
 *
 * <p>O token carrega apenas o necessário para autorizar a requisição:
 * {@code sub} (id do operador), {@code role}, {@code iss} e {@code jti}. Nome,
 * e-mail e CPF nunca entram no token. A revogação de acesso é garantida pelo
 * TTL curto e pela recusa de refresh para conta inativa.
 */
@Service
public class JwtService {

    /** Segredo de desenvolvimento; recusado no perfil de produção. */
    static final String DEV_SECRET = "dev-only-maraberto-jwt-secret-32bytes!!";

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(JwtProperties properties, Environment environment) {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "maraberto.security.jwt.secret deve ter no mínimo 32 bytes; defina JWT_SECRET");
        }
        if (DEV_SECRET.equals(secret)
                && environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException(
                    "o segredo JWT de desenvolvimento não pode ser usado no perfil de produção; "
                            + "defina JWT_SECRET com um segredo real");
        }
        if (properties.getAccessTokenTtl() == null || properties.getAccessTokenTtl().isNegative()
                || properties.getAccessTokenTtl().isZero()) {
            throw new IllegalStateException("access-token-ttl deve ser positivo");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.getIssuer();
        this.accessTokenTtl = properties.getAccessTokenTtl();
    }

    public String generateAccessToken(OperationalUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }
}
