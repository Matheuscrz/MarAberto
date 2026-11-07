package com.maraberto.media.infra;

import com.maraberto.media.api.ObjectStoragePort;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementação em memória da {@link ObjectStoragePort} para desenvolvimento e
 * testes. Não faz upload real: apenas registra metadados e devolve URLs
 * fictícias. A implementação S3 real entra na Etapa 11.
 */
@Component
@ConditionalOnProperty(
        name = "maraberto.storage.provider",
        havingValue = "stub",
        matchIfMissing = true)
public class StubObjectStorage implements ObjectStoragePort {

    private static final String STUB_SCHEME = "stub://";

    private final Map<String, ObjectMetadata> objects = new ConcurrentHashMap<>();
    private final Clock clock;

    public StubObjectStorage() {
        this(Clock.systemUTC());
    }

    public StubObjectStorage(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PresignedUrl presignPut(String bucket, String key, String contentType, Duration ttl) {
        validateTtl(ttl);
        objects.put(objectKey(bucket, key), new ObjectMetadata(bucket, key, 0L, contentType));
        return new PresignedUrl(url(bucket, key), clock.instant().plus(ttl));
    }

    @Override
    public PresignedUrl presignGet(String bucket, String key, Duration ttl) {
        validateTtl(ttl);
        return new PresignedUrl(url(bucket, key), clock.instant().plus(ttl));
    }

    @Override
    public void delete(String bucket, String key) {
        objects.remove(objectKey(bucket, key));
    }

    @Override
    public Optional<ObjectMetadata> head(String bucket, String key) {
        return Optional.ofNullable(objects.get(objectKey(bucket, key)));
    }

    private static String objectKey(String bucket, String key) {
        return bucket + "/" + key;
    }

    private static String url(String bucket, String key) {
        return STUB_SCHEME + bucket + "/" + key;
    }

    private static void validateTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl deve ser positivo");
        }
    }
}
