package com.maraberto.media.application;

import com.maraberto.media.api.MediaCatalog;
import com.maraberto.media.api.MediaView;
import com.maraberto.media.api.ObjectStoragePort;
import com.maraberto.media.domain.ImageTooLargeException;
import com.maraberto.media.domain.InvalidImageException;
import com.maraberto.media.domain.MediaAsset;
import com.maraberto.media.domain.MediaNotFoundException;
import com.maraberto.media.infra.JdbcMediaRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upload e leitura da mídia da sinalização (RF12/ADR-012).
 *
 * <p>O binário do aparelho nunca é guardado como chegou: o
 * {@link ImageNormalizer} re-codifica a imagem e o servidor guarda o JPEG
 * produzido por ele. O nome do objeto é aleatório — a chave do storage não
 * revela praia, operador nem horário.
 */
@Service
public class MediaService implements MediaCatalog {

    /** Limite do arquivo recebido; o multipart aceita um pouco mais. */
    public static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    private final JdbcMediaRepository repository;
    private final ImageNormalizer normalizer;
    private final ObjectStoragePort storage;
    private final String bucket;
    private final Clock clock = Clock.systemUTC();

    public MediaService(
            JdbcMediaRepository repository,
            ImageNormalizer normalizer,
            ObjectStoragePort storage,
            @Value("${maraberto.media.bucket:media-public}") String bucket) {
        this.repository = repository;
        this.normalizer = normalizer;
        this.storage = storage;
        this.bucket = bucket;
    }

    @Transactional
    public MediaView upload(UUID ownerId, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidImageException();
        }
        if (content.length > MAX_UPLOAD_BYTES) {
            throw new ImageTooLargeException(MAX_UPLOAD_BYTES);
        }

        NormalizedImage image = normalizer.normalize(content);
        String objectKey = objectKey();
        storage.put(bucket, objectKey, image.contentType(), image.content());

        MediaAsset asset = repository.insert(
                objectKey,
                image.contentType(),
                image.content().length,
                image.widthPx(),
                image.heightPx(),
                sha256(image.content()),
                ownerId);
        return view(asset);
    }

    @Transactional(readOnly = true)
    public StoredMedia load(UUID mediaId) {
        MediaAsset asset = repository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException(mediaId));
        byte[] content = storage.get(bucket, asset.objectKey())
                .orElseThrow(() -> new MediaNotFoundException(mediaId));
        return new StoredMedia(asset, content, etag(asset.sha256()));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findOwned(UUID ownerId, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(repository.findOwnedIds(ownerId, List.copyOf(ids)));
    }

    private String objectKey() {
        LocalDate today = LocalDate.now(clock);
        return "signals/%d/%02d/%s.jpg".formatted(
                today.getYear(), today.getMonthValue(), UUID.randomUUID());
    }

    private static MediaView view(MediaAsset asset) {
        return new MediaView(
                asset.id(),
                "/api/v1/media/" + asset.id(),
                asset.contentType(),
                asset.widthPx(),
                asset.heightPx(),
                asset.sizeBytes());
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private static String etag(byte[] digest) {
        return "\"" + HexFormat.of().formatHex(digest) + "\"";
    }

    /** Conteúdo servido e o cabeçalho de validação correspondente. */
    public record StoredMedia(MediaAsset asset, byte[] content, String etag) {

        public StoredMedia {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
