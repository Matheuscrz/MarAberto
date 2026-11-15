package com.maraberto.shared.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket local, por instância.
 *
 * <p>Existe para o caso em que o Redis não está disponível e o limite não pode
 * simplesmente desaparecer — o login é o exemplo: sem Redis, o limitador do
 * Redis falha aberto e um atacante poderia tentar senhas sem limite. Este balde
 * em memória continua barrando por processo enquanto o Redis volta.
 *
 * <p>Não é compartilhado entre instâncias e zera no restart; por isso nunca é a
 * única camada quando o Redis está saudável. A chave deve chegar já opaca (HMAC
 * do IP/conta), nunca o dado em claro.
 */
public class LocalTokenBucketRateLimiter {

    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitResult tryConsume(String key, int capacity, Duration window) {
        if (capacity <= 0 || window == null || window.isZero() || window.isNegative()) {
            return RateLimitResult.permit();
        }
        long now = System.currentTimeMillis();
        long windowMs = window.toMillis();
        double refillPerMs = capacity / (double) windowMs;

        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, now));
        boolean allowed;
        synchronized (bucket) {
            double tokens =
                    Math.min(capacity, bucket.tokens + (now - bucket.updatedAt) * refillPerMs);
            allowed = tokens >= 1;
            if (allowed) {
                tokens -= 1;
            }
            bucket.tokens = tokens;
            bucket.updatedAt = now;
        }

        if (buckets.size() > MAX_ENTRIES) {
            purgeExpired(now, windowMs * 2);
        }

        return allowed ? RateLimitResult.permit() : RateLimitResult.reject(retryAfter(windowMs, capacity));
    }

    private static long retryAfter(long windowMs, int capacity) {
        return Math.max(1, (long) Math.ceil((windowMs / (double) capacity) / 1000.0));
    }

    private void purgeExpired(long now, long ttlMs) {
        buckets.entrySet().removeIf(entry -> entry.getValue().updatedAt < now - ttlMs);
    }

    private static final class Bucket {
        private double tokens;
        private long updatedAt;

        private Bucket(double tokens, long updatedAt) {
            this.tokens = tokens;
            this.updatedAt = updatedAt;
        }
    }
}
