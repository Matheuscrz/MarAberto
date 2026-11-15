package com.maraberto.emergency.application;

import com.maraberto.emergency.domain.SosRateLimitedException;
import com.maraberto.shared.ratelimit.RateLimitResult;
import com.maraberto.shared.ratelimit.TokenBucketRateLimiter;
import com.maraberto.shared.security.crypto.BlindIndexer;
import org.springframework.stereotype.Component;

/**
 * Limite do SOS anônimo (RN05/ADR-001).
 *
 * <p>A chave é o HMAC do endereço de origem, nunca o IP em claro: o Redis guarda
 * só um índice cego e o valor não aparece em chave, log ou resposta. O limite é
 * <strong>falha aberta</strong> — sem Redis o chamado passa e a idempotência por
 * {@code clientRequestId} continua evitando duplicata.
 */
@Component
public class SosRateLimiter {

    private static final String KEY_PREFIX = "sos:rate:";

    private final TokenBucketRateLimiter limiter;
    private final BlindIndexer blindIndexer;
    private final SosRateLimitProperties properties;

    public SosRateLimiter(
            TokenBucketRateLimiter limiter,
            BlindIndexer blindIndexer,
            SosRateLimitProperties properties) {
        this.limiter = limiter;
        this.blindIndexer = blindIndexer;
        this.properties = properties;
    }

    /**
     * Consome uma ficha do balde da origem.
     *
     * @throws SosRateLimitedException quando o limite foi excedido
     */
    public void check(String clientAddress) {
        if (!properties.isEnabled() || clientAddress == null || clientAddress.isBlank()) {
            return;
        }
        String key = KEY_PREFIX + blindIndexer.index(clientAddress);
        RateLimitResult result = limiter.tryConsume(
                key, properties.getCapacity(), properties.getWindow());
        if (!result.allowed()) {
            throw new SosRateLimitedException(result.retryAfterSeconds());
        }
    }
}
