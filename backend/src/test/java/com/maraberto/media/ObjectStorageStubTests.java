package com.maraberto.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maraberto.media.api.ObjectStoragePort;
import com.maraberto.media.infra.StubObjectStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ObjectStorageStubTests {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private final ObjectStoragePort storage = new StubObjectStorage(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void registersMetadataAndGeneratesPresignedUrls() {
        ObjectStoragePort.PresignedUrl put =
                storage.presignPut("media-public", "fotos/praia.jpg", "image/jpeg", Duration.ofMinutes(10));

        assertThat(put.url()).isEqualTo("stub://media-public/fotos/praia.jpg");
        assertThat(put.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));

        assertThat(storage.head("media-public", "fotos/praia.jpg"))
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.bucket()).isEqualTo("media-public");
                    assertThat(metadata.key()).isEqualTo("fotos/praia.jpg");
                    assertThat(metadata.contentType()).isEqualTo("image/jpeg");
                });

        ObjectStoragePort.PresignedUrl get =
                storage.presignGet("media-public", "fotos/praia.jpg", Duration.ofMinutes(5));
        assertThat(get.url()).isEqualTo("stub://media-public/fotos/praia.jpg");
        assertThat(get.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void deletesIdempotently() {
        storage.presignPut("media-public", "fotos/praia.jpg", "image/jpeg", Duration.ofMinutes(10));

        storage.delete("media-public", "fotos/praia.jpg");
        storage.delete("media-public", "fotos/praia.jpg");

        assertThat(storage.head("media-public", "fotos/praia.jpg")).isEmpty();
    }

    @Test
    void rejectsInvalidTtl() {
        assertThatThrownBy(() ->
                storage.presignPut("media-public", "fotos/praia.jpg", "image/jpeg", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");
    }
}
