package com.maraberto.media.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Foto enviada em base64 pelo app empacotado.
 *
 * <p>O conteúdo é o arquivo original (JPEG ou PNG); o servidor decodifica,
 * re-codifica e aplica os mesmos limites do upload multipart.
 *
 * @param contentBase64 arquivo codificado em base64, sem o prefixo
 *                      {@code data:image/...;base64,}
 */
@Schema(name = "MediaUploadRequest", description = "Foto em base64")
public record MediaUploadRequest(
        @Schema(description = "Conteúdo do arquivo em base64", requiredMode =
                        Schema.RequiredMode.REQUIRED)
        @NotBlank String contentBase64) {
}
