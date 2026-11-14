package com.maraberto.occurrences.api;

import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Pedido de revelação dos dados de vítima. O motivo é obrigatório e entra no
 * histórico da ocorrência (RN15, RNF06).
 */
@Schema(name = "RevealVictimRequest", description = "Motivo da revelação dos dados de vítima")
public record RevealVictimRequest(
        @NotBlank
        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 200)
        @Schema(description = "Por que o dado precisa ser visto agora",
                example = "Preenchimento da ficha do CIOSP")
        String reason) {
}
