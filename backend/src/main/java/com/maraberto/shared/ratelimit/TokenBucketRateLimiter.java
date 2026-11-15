package com.maraberto.shared.ratelimit;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Token bucket atômico no Redis.
 *
 * <p>O balde é a unidade certa para o SOS anônimo: a RN05 limita a três
 * chamados por dez minutos, mas uma pessoa em perigo não pode ser punida por
 * ter tentado três vezes em um minuto e precisar de uma quarta. O balde começa
 * cheio, cada chamado consome uma ficha e as fichas voltam a uma taxa
 * constante — sem janela fixa que zera de uma vez.
 *
 * <p><strong>Falha aberta por decisão.</strong> O SOS não pode parar porque o
 * Redis caiu (ADR-001: o chamado não depende de conta nem de infraestrutura
 * auxiliar). Quando o Redis está indisponível o limitador libera a passagem e
 * registra o fato; a idempotência por {@code clientRequestId} continua
 * protegendo contra duplicata.
 *
 * <p>A chave é sempre opaca: os chamadores passam o HMAC do IP/identificador,
 * nunca o valor em claro.
 */
@Component
public class TokenBucketRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    /**
     * Consome uma ficha do balde identificado por {@code KEYS[1]}.
     *
     * <p>ARGV: capacidade, fichas por milissegundo, instante atual (ms) e TTL.
     * A TTL é o dobro da janela para o balde não desaparecer antes de encher.
     */
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refillPerMs = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local ttlMs = tonumber(ARGV[4])

            local state = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(state[1])
            local updatedAt = tonumber(state[2])
            if tokens == nil or updatedAt == nil then
              tokens = capacity
              updatedAt = now
            end

            local elapsed = now - updatedAt
            if elapsed < 0 then
              elapsed = 0
            end
            tokens = math.min(capacity, tokens + elapsed * refillPerMs)

            local allowed = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            end

            redis.call('HSET', key, 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', key, ttlMs)
            return allowed
            """, Long.class);

    private final StringRedisTemplate redis;

    public TokenBucketRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public RateLimitResult tryConsume(String key, int capacity, Duration window) {
        if (capacity <= 0 || window == null || window.isZero() || window.isNegative()) {
            return RateLimitResult.permit();
        }
        long now = System.currentTimeMillis();
        long windowMs = window.toMillis();
        double refillPerMs = capacity / (double) windowMs;
        try {
            Long allowed = redis.execute(
                    CONSUME_SCRIPT,
                    List.of(key),
                    Integer.toString(capacity),
                    Double.toString(refillPerMs),
                    Long.toString(now),
                    Long.toString(windowMs * 2));
            if (allowed == null) {
                return RateLimitResult.permit();
            }
            return allowed == 1L
                    ? RateLimitResult.permit()
                    : RateLimitResult.reject(retryAfterSeconds(windowMs, capacity));
        } catch (RuntimeException exception) {
            // Falha aberta: o SOS não pode depender do Redis (ADR-001).
            log.warn("limitador indisponível no Redis ({}); seguindo sem limitar",
                    exception.getClass().getSimpleName());
            return RateLimitResult.permit();
        }
    }

    private static long retryAfterSeconds(long windowMs, int capacity) {
        return Math.max(1, (long) Math.ceil((windowMs / (double) capacity) / 1000.0));
    }
}
