package com.maraberto.media.infra;

import com.maraberto.media.api.ObjectStoragePort;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Implementação real da porta sobre o Garage (S3-compatível, ADR-006).
 *
 * <p>As URLs pré-assinadas usam o mesmo endpoint do cliente; o {@link #put}
 * existe para o arquivo que já chegou ao backend (KML da importação), evitando
 * um ida-e-volta HTTP só para enviar o que está em memória.
 */
@Component
@ConditionalOnProperty(name = "maraberto.storage.provider", havingValue = "s3")
public class S3ObjectStorage implements ObjectStoragePort {

    private final S3Client s3;
    private final S3Presigner presigner;

    public S3ObjectStorage(S3Client s3, S3Presigner presigner) {
        this.s3 = s3;
        this.presigner = presigner;
    }

    @Override
    public PresignedUrl presignPut(String bucket, String key, String contentType, Duration ttl) {
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build())
                .build());
        return new PresignedUrl(presigned.url().toString(), presigned.expiration());
    }

    @Override
    public PresignedUrl presignGet(String bucket, String key, Duration ttl) {
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())
                .build());
        return new PresignedUrl(presigned.url().toString(), presigned.expiration());
    }

    @Override
    public void delete(String bucket, String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public ObjectMetadata put(String bucket, String key, String contentType, byte[] content) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));
        return new ObjectMetadata(bucket, key, content.length, contentType);
    }

    @Override
    public Optional<ObjectMetadata> head(String bucket, String key) {
        try {
            var head = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new ObjectMetadata(
                    bucket, key, head.contentLength(), head.contentType()));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }
}
