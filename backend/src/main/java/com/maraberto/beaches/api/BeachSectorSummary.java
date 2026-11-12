package com.maraberto.beaches.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Setor operacional de uma área de banho.
 *
 * <p>O nome é rótulo, não identidade: 20 das 45 feições reais de Sergipe vêm
 * sem nome. A UI mostra "Setor N" pela ordem quando {@code name} é nulo.
 */
@Schema(name = "PublicBeachSector", description = "Setor operacional de uma área de banho")
public record BeachSectorSummary(
        @Schema(description = "Identificador estável do setor") UUID id,
        @Schema(description = "Nome do setor; nulo quando o KML não traz nome") String name,
        @Schema(description = "Ordem de exibição dentro da área", example = "1") int displayOrder,
        @Schema(description = "Latitude do centro do setor") double latitude,
        @Schema(description = "Longitude do centro do setor") double longitude) {
}
