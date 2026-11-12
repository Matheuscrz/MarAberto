package com.maraberto.beaches.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Setor de uma área de banho com a geometria pronta para o mapa.
 *
 * <p>A geometria já sai simplificada do banco ({@code
 * ST_SimplifyPreserveTopology}); o nome continua sendo rótulo e pode ser nulo
 * (a UI mostra "Setor N" pela {@code displayOrder}).
 */
@Schema(name = "BeachMapSector", description = "Setor com geometria GeoJSON para o mapa")
public record BeachMapSector(
        @Schema(description = "Identificador estável do setor") UUID id,
        @Schema(description = "Nome do setor; nulo quando o KML não traz nome") String name,
        @Schema(description = "Ordem de exibição dentro da área", example = "1") int displayOrder,
        @Schema(description = "Retângulo que contém o setor") BeachMapBounds bbox,
        @Schema(description = "Geometria GeoJSON (Polygon ou MultiPolygon) em WGS 84",
                type = "object") JsonNode geometry) {
}
