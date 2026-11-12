package com.maraberto.occupancy.application;

import com.maraberto.occupancy.domain.OccupancyLevel;
import com.maraberto.occupancy.domain.OccupancyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Leitura pública do indicador de ocupação.
 *
 * <p>Quando a amostra é menor que o mínimo (RN06) o nível vem nulo: o cliente
 * mostra "dados insuficientes" em vez de um número que permitiria inferir
 * quantas pessoas estão no setor.
 *
 * @param windowMinutes janela móvel usada no cálculo
 */
@Schema(name = "OccupancyEstimate", description = "Faixa de ocupação estimada da área de banho")
public record OccupancyEstimate(
        @Schema(description = "Área de banho consultada") UUID beachId,
        @Schema(description = "INSUFFICIENT quando não há amostra mínima") OccupancyStatus status,
        @Schema(description = "Faixa exibida; nulo quando INSUFFICIENT") OccupancyLevel level,
        @Schema(description = "Janela móvel em minutos", example = "15") int windowMinutes) {
}
