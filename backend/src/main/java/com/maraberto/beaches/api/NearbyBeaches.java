package com.maraberto.beaches.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Resposta da consulta por proximidade.
 *
 * @param radiusMeters raio efetivamente aplicado, em metros
 */
@Schema(name = "NearbyBeaches", description = "Áreas de banho dentro do raio consultado")
public record NearbyBeaches(
        @Schema(description = "Raio aplicado em metros", example = "30000") int radiusMeters,
        @Schema(description = "Áreas ordenadas da mais próxima para a mais distante")
        List<BeachSummary> beaches) {
}
