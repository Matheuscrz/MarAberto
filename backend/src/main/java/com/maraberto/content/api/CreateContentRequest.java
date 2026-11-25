package com.maraberto.content.api;

import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Criação de conteúdo educativo ou material de divulgação pela coordenação.
 */
public record CreateContentRequest(
        @NotNull(message = "Informe o tipo de conteúdo")
        ContentKind kind,

        @NotBlank(message = "Informe o título")
        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 160)
        String title,

        @NotBlank(message = "Informe a descrição")
        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 2000)
        String description,

        @NotBlank(message = "Informe a categoria")
        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 60)
        String category,

        @Size(max = 12, message = "Informe no máximo 12 tags")
        List<@NotBlank @Size(max = 40, message = "Cada tag deve ter no máximo 40 caracteres") String> tags,

        @Sanitized(value = SanitizationPolicy.URL, maxLength = 500)
        @Pattern(regexp = "^https?://\\S+$", message = "URL externa inválida; use http:// ou https://")
        String externalUrl,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 80)
        String externalLinkLabel,

        @Sanitized(value = SanitizationPolicy.YOUTUBE_ID, maxLength = 11)
        @Pattern(regexp = "^[A-Za-z0-9_-]{11}$", message = "ID do vídeo do YouTube deve ter 11 caracteres válidos")
        String youtubeId,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 16)
        String duration,

        @Min(value = 0, message = "A ordem de exibição não pode ser negativa")
        Integer displayOrder,

        Boolean published) {
}
