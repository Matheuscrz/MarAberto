package com.maraberto.occurrences.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Ficha completa da ocorrência.
 *
 * <p>O resumo e as observações são decifrados só aqui, em rota autenticada e
 * restrita ao escopo de quem consulta. Dados de vítima continuam mascarados:
 * para vê-los é preciso uma revelação explícita, com motivo e auditoria
 * (RN15).
 */
@Schema(name = "OccurrenceDetail", description = "Ocorrência com o relato do atendimento")
public record OccurrenceDetailView(
        @Schema(description = "Dados da lista") OccurrenceView occurrence,
        @Schema(description = "Resumo do atendimento (texto cifrado no banco)") String summary,
        @Schema(description = "Observações do desfecho") String outcomeNotes,
        @Schema(description = "Ponto de referência informado em campo") String referenceText,
        @Schema(description = "Protocolo do 193 ou de outro canal de origem")
        String externalProtocol,
        @Schema(description = "Protocolo gerado no CIOSP") String ciospProtocol,
        @Schema(description = "Quando o CIOSP foi registrado") Instant ciospRegisteredAt,
        @Schema(description = "Quem encerrou a ocorrência") UUID closedBy,
        String closedByName,
        @Schema(description = "Dados de vítima, mascarados por padrão") VictimView victim,
        @Schema(description = "Versão da ficha para concorrência", example = "3") long version) {
}
