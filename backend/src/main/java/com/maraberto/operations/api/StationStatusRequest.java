package com.maraberto.operations.api;

import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ativação/desativação de posto ou base móvel no painel.
 *
 * @param active     {@code false} tira a estrutura do mapa público e da lista de destinos
 * @param statusNote motivo da mudança, exibido no painel (ex.: "em reforma")
 */
@Schema(name = "StationStatusRequest", description = "Estado de ativação da estrutura")
public record StationStatusRequest(
        @Schema(description = "Estrutura em operação", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Boolean active,
        @Schema(description = "Motivo da desativação ou observação do painel", maxLength = 200)
        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 200)
        @Size(max = 200) String statusNote) {
}
