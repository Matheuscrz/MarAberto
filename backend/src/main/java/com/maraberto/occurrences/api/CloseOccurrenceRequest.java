package com.maraberto.occurrences.api;

import com.maraberto.occurrences.domain.OccurrenceNature;
import com.maraberto.occurrences.domain.OccurrenceOutcome;
import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Encerramento do atendimento em campo.
 *
 * <p>O guarda-vidas responsável registra o que aconteceu e o desfecho; a
 * ocorrência sai da fila "em atendimento" e o chamado do SOS é encerrado no
 * mesmo passo. A coordenação pode completar os dados depois pela edição.
 */
@Schema(name = "CloseOccurrenceRequest", description = "Dados do encerramento do atendimento")
public record CloseOccurrenceRequest(
        @NotNull
        @Schema(description = "Desfecho do atendimento", example = "RESOLVED")
        OccurrenceOutcome outcome,

        @Schema(description = "Natureza corrigida durante o atendimento")
        OccurrenceNature nature,

        @NotBlank
        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 2000)
        @Schema(description = "O que aconteceu e como foi o atendimento")
        String summary,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 2000)
        @Schema(description = "Observações do desfecho/encaminhamento")
        String outcomeNotes,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 160)
        @Schema(description = "Ponto de referência do atendimento")
        String referenceText,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 40)
        @Schema(description = "Protocolo do 193 ou de outro canal")
        String externalProtocol,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 40)
        @Schema(description = "Protocolo gerado no CIOSP, quando já houver")
        String ciospProtocol,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 120)
        @Schema(description = "Nome da vítima, se identificada")
        String victimName,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 20)
        @Schema(description = "Idade da vítima, se informada")
        String victimAge,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 80)
        @Schema(description = "Contato da vítima ou do responsável")
        String victimContact,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 500)
        @Schema(description = "Condição da vítima e atendimento prestado")
        String victimCondition) {
}
