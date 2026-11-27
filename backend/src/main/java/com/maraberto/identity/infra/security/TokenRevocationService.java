package com.maraberto.identity.infra.security;

import com.maraberto.shared.redis.RedisAvailabilityGuard;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Revogação imediata de access tokens JWT (blacklist por {@code jti} e corte
 * por usuário {@code tokensValidAfter}).
 *
 * <p>Usa Redis quando disponível (compartilhado entre réplicas) e mantém um
 * mapa local em memória como fallback degradado para que o logout e a
 * desativação de conta continuem funcionando mesmo sem Redis.
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);
    private static final String JTI_PREFIX = "maraberto:jwt:revoked:jti:";
    private static final String USER_PREFIX = "maraberto:jwt:revoked:user:";

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final Duration accessTokenTtl;
    private final RedisAvailabilityGuard redisGuard;
    private final ConcurrentMap<String, Instant> localRevokedJtis = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Instant> localUserCutoff = new ConcurrentHashMap<>();

    @Autowired
    public TokenRevocationService(
            ObjectProvider<StringRedisTemplate> redisProvider,
            JwtProperties jwtProperties,
            RedisAvailabilityGuard redisGuard) {
        this.redisProvider = redisProvider;
        this.accessTokenTtl = jwtProperties.getAccessTokenTtl();
        this.redisGuard = redisGuard;
    }

    public TokenRevocationService(
            ObjectProvider<StringRedisTemplate> redisProvider,
            JwtProperties jwtProperties) {
        this(redisProvider, jwtProperties, new RedisAvailabilityGuard(Duration.ofSeconds(15)));
    }

    /** Revoga um único access token (ex.: logout). */
    public void revokeToken(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        Instant effectiveExpiry = (expiresAt != null && expiresAt.isAfter(now))
                ? expiresAt
                : now.plus(accessTokenTtl);
        Duration ttl = Duration.between(now, effectiveExpiry);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }

        localRevokedJtis.put(jti, effectiveExpiry);
        pruneLocalJtis(now);

        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null && redisGuard.allowAttempt()) {
            try {
                redis.opsForValue().set(JTI_PREFIX + jti, "1", ttl);
                redisGuard.recordSuccess();
            } catch (RuntimeException exception) {
                redisGuard.recordFailure(exception);
                log.debug("Redis indisponível ao registrar blacklist de jti; usando fallback local", exception);
            }
        }
    }

    /**
     * Invalida todos os access tokens emitidos antes de {@code revokedAt} para
     * o usuário (ex.: desativação, mudança de papel, reset/troca de senha).
     */
    public void revokeAllForUser(UUID userId, Instant revokedAt) {
        if (userId == null) {
            return;
        }
        // JWT `iat` (RFC 7519) tem granularidade de segundos: truncar evita
        // rejeitar um novo login emitido no mesmo segundo logo após a troca de senha.
        Instant cutoff = (revokedAt != null ? revokedAt : Instant.now()).truncatedTo(ChronoUnit.SECONDS);
        localUserCutoff.put(userId, cutoff);

        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null && redisGuard.allowAttempt()) {
            try {
                redis.opsForValue().set(
                        USER_PREFIX + userId,
                        Long.toString(cutoff.toEpochMilli()),
                        accessTokenTtl.plusSeconds(30));
                redisGuard.recordSuccess();
            } catch (RuntimeException exception) {
                redisGuard.recordFailure(exception);
                log.debug("Redis indisponível ao registrar revogação por usuário; usando fallback local", exception);
            }
        }
    }

    /** Verifica se o token foi revogado por {@code jti} ou por corte do usuário. */
    public boolean isRevoked(String jti, UUID userId, Instant issuedAt) {
        Instant now = Instant.now();
        if (jti != null && !jti.isBlank()) {
            Instant localExp = localRevokedJtis.get(jti);
            if (localExp != null) {
                if (localExp.isAfter(now)) {
                    return true;
                }
                localRevokedJtis.remove(jti, localExp);
            }
        }
        if (userId != null && issuedAt != null) {
            Instant cutoff = localUserCutoff.get(userId);
            if (cutoff != null && issuedAt.isBefore(cutoff)) {
                return true;
            }
        }

        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null && redisGuard.allowAttempt()) {
            try {
                if (jti != null && !jti.isBlank() && Boolean.TRUE.equals(redis.hasKey(JTI_PREFIX + jti))) {
                    redisGuard.recordSuccess();
                    return true;
                }
                if (userId != null && issuedAt != null) {
                    String rawCutoff = redis.opsForValue().get(USER_PREFIX + userId);
                    if (rawCutoff != null) {
                        long cutoffMillis = Long.parseLong(rawCutoff);
                        if (issuedAt.toEpochMilli() < cutoffMillis) {
                            redisGuard.recordSuccess();
                            return true;
                        }
                    }
                }
                redisGuard.recordSuccess();
            } catch (RuntimeException exception) {
                redisGuard.recordFailure(exception);
                log.debug("Redis indisponível ao consultar blacklist JWT; seguindo com estado local", exception);
            }
        }
        return false;
    }

    private void pruneLocalJtis(Instant now) {
        if (localRevokedJtis.size() > 1000) {
            localRevokedJtis.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        }
    }
}
