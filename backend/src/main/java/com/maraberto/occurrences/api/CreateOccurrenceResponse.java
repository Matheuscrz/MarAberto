package com.maraberto.occurrences.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Resposta do registro manual de ocorrência (RF33, RN13).
 */
@Schema(name = "CreateOccurrenceResponse",
        description = "Ficha criada e eventuais alertas não bloqueantes de duplicata")
public record CreateOccurrenceResponse(
        @Schema(description = "Ficha completa da ocorrência criada")
        OccurrenceDetailView detail,
        @Schema(description = "Registros próximos no espaço e no tempo (aviso não bloqueante)")
        List<DuplicateCandidateView> possibleDuplicates) {
}
