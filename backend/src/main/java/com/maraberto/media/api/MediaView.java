package com.maraberto.media.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Referência pública de uma mídia já guardada.
 *
 * <p>A URL é do próprio backend, não uma URL pré-assinada: a foto da
 * sinalização aparece em tela pública e não pode expirar. O binário muda de
 * conteúdo só quando o hash muda, então ele é cacheável como imutável.
 */
@Schema(name = "MediaView", description = "Foto guardada e pronta para exibição")
public record MediaView(
        @Schema(description = "Identificador da mídia") UUID id,
        @Schema(description = "Caminho público da imagem", example = "/api/v1/media/8f1c…")
        String url,
        @Schema(description = "Tipo MIME servido", example = "image/jpeg") String contentType,
        @Schema(description = "Largura em pixels", example = "1600") Integer widthPx,
        @Schema(description = "Altura em pixels", example = "1200") Integer heightPx,
        @Schema(description = "Tamanho normalizado em bytes") long sizeBytes) {
}
