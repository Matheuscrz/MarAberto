package com.maraberto.media.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Foto já normalizada e guardada no armazenamento de objetos.
 *
 * <p>O binário que o aparelho manda nunca é guardado como chegou: ele é
 * re-codificado, o que remove EXIF/GPS e garante que o arquivo servido é uma
 * imagem de verdade. O {@code sha256} é do conteúdo normalizado e serve para
 * auditoria e deduplicação.
 */
public record MediaAsset(
        UUID id,
        String objectKey,
        String contentType,
        long sizeBytes,
        Integer widthPx,
        Integer heightPx,
        byte[] sha256,
        UUID ownerId,
        Instant createdAt) {
}
