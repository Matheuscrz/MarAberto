package com.maraberto.identity.application;

import com.maraberto.identity.domain.TooManyLoginAttemptsException;
import com.maraberto.identity.infra.security.LoginProtectionProperties;
import com.maraberto.shared.ratelimit.LocalTokenBucketRateLimiter;
import com.maraberto.shared.ratelimit.RateLimitResult;
import com.maraberto.shared.ratelimit.TokenBucketRateLimiter;
import com.maraberto.shared.security.crypto.BlindIndexer;
import org.springframework.stereotype.Component;

/**
 * Limite de tentativas de login por origem.
 *
 * <p>São dois baldes com a mesma capacidade: o do Redis (compartilhado entre
 * instâncias) e o local. O local existe porque o limitador do Redis falha
 * aberto — sem ele, uma queda do Redis tiraria o limite do login justamente
 * quando o ataque poderia continuar. A chave chega ao Redis como HMAC do IP.
 */
@Component
public class LoginIpRateLimiter {

    private static final String KEY_PREFIX = "login:ip:";

    private final TokenBucketRateLimiter redisLimiter;
    private final LocalTokenBucketRateLimiter localLimiter = new LocalTokenBucketRateLimiter();
    private final BlindIndexer blindIndexer;
    private final LoginProtectionProperties properties;

    public LoginIpRateLimiter(
            TokenBucketRateLimiter redisLimiter,
            BlindIndexer blindIndexer,
            LoginProtectionProperties properties) {
        this.redisLimiter = redisLimiter;
        this.blindIndexer = blindIndexer;
        this.properties = properties;
    }

    /**
     * Consome uma tentativa da origem.
     *
     * @throws TooManyLoginAttemptsException quando o balde local ou o do Redis
     *         acabou
     */
    public void check(String clientAddress) {
        if (!properties.isIpEnabled() || clientAddress == null || clientAddress.isBlank()) {
            return;
        }
        String key = KEY_PREFIX + blindIndexer.index(clientAddress);

        RateLimitResult local = localLimiter.tryConsume(
                key, properties.getIpCapacity(), properties.getIpWindow());
        if (!local.allowed()) {
            throw new TooManyLoginAttemptsException(local.retryAfterSeconds());
        }

        RateLimitResult shared = redisLimiter.tryConsume(
                key, properties.getIpCapacity(), properties.getIpWindow());
        if (!shared.allowed()) {
            throw new TooManyLoginAttemptsException(shared.retryAfterSeconds());
        }
    }
}
