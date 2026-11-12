package com.maraberto.occupancy.application;

import com.maraberto.occupancy.domain.OccupancyLevel;
import com.maraberto.occupancy.domain.OccupancyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Faixa de ocupação de um setor dentro da área.
 *
 * <p>O mínimo de amostras é o mesmo da área (RN06): com menos gente, a faixa
 * permitiria inferir quantas pessoas estão num setor vazio. Setor pequeno
 * responder {@code INSUFFICIENT} é o comportamento correto.
 */
@Schema(name = "OccupancySector", description = "Faixa de ocupação estimada de um setor")
public record OccupancySector(
        @Schema(description = "Setor consultado") UUID sectorId,
        @Schema(description = "INSUFFICIENT quando o setor não atinge a amostra mínima")
        OccupancyStatus status,
        @Schema(description = "Faixa exibida; nulo quando INSUFFICIENT") OccupancyLevel level) {
}
