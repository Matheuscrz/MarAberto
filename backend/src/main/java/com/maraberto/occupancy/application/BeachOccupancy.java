package com.maraberto.occupancy.application;

import com.maraberto.occupancy.domain.OccupancyLevel;
import com.maraberto.occupancy.domain.OccupancyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Leitura pública da ocupação de uma área e dos seus setores.
 *
 * <p><strong>A faixa da área é a união das amostras, nunca a soma dos
 * setores.</strong> Quem está na fronteira de dois setores aparece nos dois;
 * somar inflaria o número. Por isso a área tem contagem própria, e este
 * contrato repete as duas leituras em vez de derivar uma da outra.
 *
 * <p>Abaixo da amostra mínima (RN06) o nível vem nulo nos dois escopos.
 *
 * @param windowMinutes janela móvel usada no cálculo
 */
@Schema(name = "BeachOccupancy",
        description = "Faixa de ocupação da área (união das amostras) e de cada setor")
public record BeachOccupancy(
        @Schema(description = "Área de banho consultada") UUID beachId,
        @Schema(description = "INSUFFICIENT quando não há amostra mínima na área")
        OccupancyStatus status,
        @Schema(description = "Faixa da área; nulo quando INSUFFICIENT") OccupancyLevel level,
        @Schema(description = "Janela móvel em minutos", example = "15") int windowMinutes,
        @Schema(description = "Setores ativos na ordem do mapeamento; nunca somar à área")
        List<OccupancySector> sectors) {
}
