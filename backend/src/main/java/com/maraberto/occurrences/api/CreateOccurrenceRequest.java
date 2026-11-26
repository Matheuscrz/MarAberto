package com.maraberto.occurrences.api;

import com.maraberto.occurrences.domain.OccurrenceNature;
import com.maraberto.occurrences.domain.OccurrenceOrigin;
import com.maraberto.occurrences.domain.OccurrenceOutcome;
import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro manual de ocorrência externa ou em campo (RF33, RN11, RN12, ADR-013).
 *
 * <p>Aceita {@code CBM_193}, {@code EXTERNAL_OTHER} (rádio, presencial, outro
 * posto) e {@code FIELD_ATTENDED} (atendimento feito sem chamado prévio).
 * Exige coordenada geográfica ou praia + ponto de referência (RN12).
 */
@Schema(name = "CreateOccurrenceRequest", description = "Dados para registro manual de ocorrência")
public record CreateOccurrenceRequest(
        @NotNull
        @Schema(description = "Identificador idempotente gerado pelo cliente (UUID)",
                example = "8d6a5a87-1c43-4af4-9b7f-9f2d5a5a4f5c")
        UUID clientRequestId,

        @NotNull
        @Schema(description = "Canal de origem (CBM_193, EXTERNAL_OTHER ou FIELD_ATTENDED)",
                example = "FIELD_ATTENDED")
        OccurrenceOrigin origin,

        @NotNull
        @Schema(description = "Natureza do atendimento", example = "RESCUE")
        OccurrenceNature nature,

        @NotNull
        @Schema(description = "Data e hora em que o fato ocorreu (ISO-8601)",
                example = "2026-11-25T14:30:00Z")
        Instant occurredAt,

        @Schema(description = "Latitude WGS84 do atendimento", example = "-10.996")
        Double latitude,

        @Schema(description = "Longitude WGS84 do atendimento", example = "-37.045")
        Double longitude,

        @Schema(description = "Precisão estimada da coordenada em metros", example = "12.5")
        Double accuracyMeters,

        @Schema(description = "Área de banho; obrigatória com referenceText quando não há coordenada")
        UUID beachId,

        @Schema(description = "Setor operacional dentro da área de banho")
        UUID sectorId,

        @Schema(description = "Posto ou base móvel vinculada")
        UUID postId,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 160)
        @Schema(description = "Ponto de referência; obrigatório quando não há coordenada")
        String referenceText,

        @NotBlank
        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 2000)
        @Schema(description = "Resumo curto do atendimento")
        String summary,

        @Schema(description = "Desfecho, quando o registro já é feito ao encerrar")
        OccurrenceOutcome outcome,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 2000)
        @Schema(description = "Observações do desfecho/encaminhamento")
        String outcomeNotes,

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
        @Schema(description = "Idade ou faixa etária da vítima, se informada")
        String victimAge,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 80)
        @Schema(description = "Contato da vítima ou do responsável")
        String victimContact,

        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 500)
        @Schema(description = "Condição da vítima e atendimento prestado")
        String victimCondition) {
}
