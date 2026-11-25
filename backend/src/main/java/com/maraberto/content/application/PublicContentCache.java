package com.maraberto.content.application;

import com.maraberto.content.api.ContentChanged;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cache curto da leitura pública da Central Aprender ({@code /api/v1/content}).
 *
 * <p>Segue o mesmo padrão do {@code PublicMapCache}: invalidação em
 * {@code AFTER_COMMIT} via {@link ContentChanged} com controle de geração para
 * impedir que uma leitura em voo repovoe o cache com o estado anterior.
 */
@Service
public class PublicContentCache {

    private static final int MAX_ENTRIES = 64;

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private final Duration ttl;

    public PublicContentCache(
            @Value("${maraberto.public-content.cache-ttl:PT10S}") Duration ttl) {
        this.ttl = ttl == null || ttl.isNegative() ? Duration.ZERO : ttl;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        if (ttl.isZero()) {
            return loader.get();
        }
        Instant now = Instant.now();
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.expiresAt().isAfter(now)) {
            return (T) entry.value();
        }
        long startedAt = generation.get();
        T value = loader.get();
        if (generation.get() == startedAt) {
            if (cache.size() >= MAX_ENTRIES) {
                cache.clear();
            }
            cache.put(key, new CacheEntry(value, now.plus(ttl)));
        }
        return value;
    }

    public void clear() {
        generation.incrementAndGet();
        cache.clear();
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onContentChanged(ContentChanged event) {
        clear();
    }

    private record CacheEntry(Object value, Instant expiresAt) {
    }
}
