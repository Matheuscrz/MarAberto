package com.maraberto.media.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Porta de armazenamento de objetos compatível com S3.
 *
 * <p>O domínio e os casos de uso dependem apenas desta interface. A
 * implementação real (Garage, S3 gerenciado, SeaweedFS ou Ceph RGW) entra na
 * Etapa 11; até lá, {@code StubObjectStorage} atende o perfil de
 * desenvolvimento e os testes.
 */
public interface ObjectStoragePort {

    /**
     * Gera uma URL temporária para envio (PUT) de um objeto.
     */
    PresignedUrl presignPut(String bucket, String key, String contentType, Duration ttl);

    /**
     * Gera uma URL temporária para leitura (GET) de um objeto.
     */
    PresignedUrl presignGet(String bucket, String key, Duration ttl);

    /**
     * Remove um objeto. Operação idempotente: remover algo inexistente não falha.
     */
    void delete(String bucket, String key);

    /**
     * Consulta metadados do objeto, se ele existir.
     */
    Optional<ObjectMetadata> head(String bucket, String key);

    /**
     * URL pré-assinada e o instante em que ela expira.
     */
    @Schema(name = "PresignedUrl", description = "URL temporária para acesso a um objeto")
    record PresignedUrl(
            @Schema(description = "URL pré-assinada")
            String url,
            @Schema(description = "Instante de expiração em UTC")
            Instant expiresAt) {

        public PresignedUrl {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("url é obrigatória");
            }
            if (expiresAt == null) {
                throw new IllegalArgumentException("expiresAt é obrigatório");
            }
        }
    }

    /**
     * Metadados mínimos de um objeto armazenado.
     */
    @Schema(name = "ObjectMetadata", description = "Metadados mínimos de um objeto armazenado")
    record ObjectMetadata(
            @Schema(description = "Nome do bucket")
            String bucket,
            @Schema(description = "Chave do objeto")
            String key,
            @Schema(description = "Tamanho do objeto em bytes", minimum = "0")
            long sizeBytes,
            @Schema(description = "Tipo MIME do objeto", example = "image/jpeg")
            String contentType) {

        public ObjectMetadata {
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalArgumentException("bucket é obrigatório");
            }
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("key é obrigatória");
            }
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("sizeBytes não pode ser negativo");
            }
        }
    }
}
