package com.maraberto.occurrences.api;

import com.maraberto.occurrences.domain.CiospStatus;
import com.maraberto.occurrences.domain.OccurrenceNature;
import com.maraberto.occurrences.domain.OccurrenceOrigin;
import com.maraberto.occurrences.domain.OccurrenceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Ficha resumida da ocorrência para o console.
 *
 * <p>A coordenada é a do chamado e só existe em rota autenticada: o console
 * operacional precisa dela para chegar ao banhista, o mapa público não a vê
 * (RN17).
 */
@Schema(name = "Occurrence", description = "Ocorrência operacional do atendimento")
public record OccurrenceView(
        @Schema(description = "Identificador") UUID id,
        @Schema(description = "Número interno legível", example = "42") long number,
        @Schema(description = "Canal de origem") OccurrenceOrigin origin,
        @Schema(description = "Natureza do atendimento") OccurrenceNature nature,
        @Schema(description = "Situação") OccurrenceStatus status,
        @Schema(description = "Quando o fato ocorreu (abertura do chamado no SOS)")
        Instant occurredAt,
        @Schema(description = "Quando a ocorrência foi criada (instante do ack)") Instant createdAt,
        @Schema(description = "Quando o chamado foi assumido") Instant acknowledgedAt,
        @Schema(description = "Encerramento, quando houver") Instant closedAt,
        @Schema(description = "SOS que originou a ocorrência") UUID emergencyCallId,
        @Schema(description = "Área de banho resolvida pelo ponto do chamado") UUID beachId,
        String beachName,
        @Schema(description = "Setor resolvido pelo ponto do chamado") UUID sectorId,
        @Schema(description = "Rótulo do setor para exibição", example = "Setor Norte")
        String sectorLabel,
        @Schema(description = "Posto que assumiu o chamado") UUID postId,
        String postName,
        @Schema(description = "Latitude do chamado", example = "-10.996") Double latitude,
        @Schema(description = "Longitude do chamado", example = "-37.045") Double longitude,
        Double accuracyMeters,
        @Schema(description = "Pendência de transcrição para o CIOSP") CiospStatus ciospStatus) {
}
