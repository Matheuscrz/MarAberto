package com.maraberto.occurrences.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro próximo no espaço e no tempo detectado no registro manual (RN13).
 *
 * <p>Nunca bloqueia o registro: serve apenas como alerta para o operador
 * decidir se trata de atendimento distinto ou relacionado.
 */
@Schema(name = "DuplicateCandidate", description = "Possível duplicata próxima (≤ 200 m e ≤ 30 min)")
public record DuplicateCandidateView(
        @Schema(description = "Tipo do registro próximo: OCCURRENCE ou EMERGENCY_CALL",
                example = "OCCURRENCE")
        String kind,
        @Schema(description = "Identificador do registro próximo") UUID id,
        @Schema(description = "Número interno, quando for ocorrência", example = "42") Long number,
        @Schema(description = "Canal de origem", example = "SOS_CITIZEN") String origin,
        @Schema(description = "Situação atual", example = "IN_PROGRESS") String status,
        @Schema(description = "Instante do fato ou chamado") Instant occurredAt,
        @Schema(description = "Distância aproximada em metros", example = "45.2")
        double distanceMeters) {
}
