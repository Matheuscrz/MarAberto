package com.maraberto.beaches.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.maraberto.beaches.domain.BeachKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Geometria de uma área de banho e dos seus setores para desenhar no mapa.
 *
 * <p>Existe como endpoint separado porque a lista por proximidade é chamada a
 * cada abertura do mapa: 18 polígonos não podem viajar junto com o resumo. A
 * geometria é simplificada para o zoom de bairro e a resposta tem {@code ETag}
 * por versão do mapa, então reabrir a mesma praia responde {@code 304}.
 */
@Schema(name = "BeachMap", description = "Geometria da área de banho e dos setores ativos")
public record BeachMap(
        @Schema(description = "Identificador estável da área") UUID id,
        @Schema(description = "Nome da praia, rio ou barragem") String name,
        @Schema(description = "Tipo da área de banho") BeachKind kind,
        @Schema(description = "Retângulo que contém a área inteira") BeachMapBounds bbox,
        @Schema(description = "Geometria GeoJSON (Polygon ou MultiPolygon) da área em WGS 84",
                type = "object") JsonNode geometry,
        @Schema(description = "Setores ativos, na ordem do mapeamento")
        List<BeachMapSector> sectors) {
}
