package com.maraberto.media.application;

/**
 * Imagem pronta para armazenar: JPEG re-codificado, sem metadados do aparelho.
 */
public record NormalizedImage(
        byte[] content,
        String contentType,
        int widthPx,
        int heightPx) {
}
