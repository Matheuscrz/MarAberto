package com.maraberto.operations.api;

import com.maraberto.operations.domain.UnmappedSignalStatus;
import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Decisão da coordenação sobre um registro de área não identificada.
 *
 * <p>{@code IDENTIFIED} exige {@code beachId} e aceita {@code sectorId} da mesma
 * área; {@code NEEDS_MAPPING} e {@code DISCARDED} não podem associar área;
 * {@code PENDING} reabre o registro e limpa a decisão anterior.
 *
 * @param decision decisão da triagem
 * @param beachId  área existente escolhida na identificação
 * @param sectorId setor da área; nulo identifica a área inteira
 * @param notes    justificativa da coordenação
 */
@Schema(name = "UnmappedSignalDecisionRequest",
        description = "Decisão da coordenação sobre um registro de área não identificada")
public record UnmappedSignalDecisionRequest(
        @Schema(description = "Decisão da triagem", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UnmappedSignalStatus decision,
        @Schema(description = "Área existente, obrigatória em IDENTIFIED")
        UUID beachId,
        @Schema(description = "Setor da área; nulo identifica a área inteira")
        UUID sectorId,
        @Schema(description = "Justificativa da triagem", maxLength = 500)
        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 500)
        @Size(max = 500) String notes) {
}
