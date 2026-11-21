package com.maraberto.operations.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Equipe em plantão na visão da coordenação (RF40/RN17).
 *
 * <p>Cada linha é um plantão aberto **com lease vivo**, com a última posição
 * conhecida — um ponto sobrescrito, nunca um trajeto. A rota é autenticada e
 * {@code no-store}: coordenada de operador não vai para cache, log ou rota
 * pública.
 *
 * @param generatedAt instante da leitura; o cliente usa para mostrar o atraso
 * @param members     plantões abertos, do sinal mais antigo para o mais recente
 */
@Schema(name = "ManagementTeam", description = "Plantões abertos e volume do dia por operador")
public record ManagementTeamView(
        Instant generatedAt,
        List<Member> members) {

    @Schema(name = "ManagementTeamMember", description = "Plantão aberto de um operador")
    public record Member(
            @Schema(description = "Plantão (shift session)") UUID shiftId,
            @Schema(description = "Operador responsável pelo turno") UUID userId,
            @Schema(description = "Nome do operador", example = "Sgt. Silva") String name,
            @Schema(description = "Matrícula funcional, quando cadastrada", example = "BM-10492")
            String badgeNumber,
            @Schema(description = "Posto fixo ou base móvel do turno", example = "Posto Pajuçara 01")
            String stationName,
            @Schema(description = "Área de banho resolvida do posto ou da posição",
                    example = "Praia de Pajuçara")
            String beachName,
            @Schema(description = "Setor da última posição, quando o ponto cai no mapeamento",
                    example = "Setor Norte")
            String sectorLabel,
            @Schema(description = "Início do plantão") Instant onShiftSince,
            @Schema(description = "Último sinal de vida do turno (lease)") Instant lastSeenAt,
            @Schema(description = "O lease ainda está vivo; a lista devolve só plantões vivos")
            boolean leaseAlive,
            @Schema(description = "Última posição conhecida; nula quando o console não enviou GPS")
            Position lastPosition,
            @Schema(description = "Ocorrências do operador desde a meia-noite do servidor",
                    example = "3")
            int occurrencesToday,
            @Schema(description = "Atendimentos em campo (sem chamado prévio) do dia",
                    example = "2")
            int fieldAttendedToday,
            @Schema(description = "Sinalizações lançadas pelo operador no dia", example = "1")
            int signalsToday,
            @Schema(description = "Ocorrências em andamento com CIOSP pendente", example = "1")
            int ciospPending) {
    }

    /**
     * Último ponto conhecido do turno.
     *
     * @param stale posição mais velha que {@code position-stale-after}; aparece
     *              como "sem sinal", nunca como atual
     */
    @Schema(name = "ManagementTeamPosition", description = "Ponto único do plantão em curso")
    public record Position(
            double latitude,
            double longitude,
            Double accuracyMeters,
            Instant at,
            boolean stale) {
    }
}
