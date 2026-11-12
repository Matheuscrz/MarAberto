package com.maraberto.beaches.api;

import com.maraberto.beaches.domain.BeachKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Área de banho no mapa público: o suficiente para a lista e o marcador.
 *
 * <p>Não carrega descrição, bandeira nem ocupação porque esses dados têm
 * ciclos de vida próprios (sinalização e amostras de ocupação) e não são
 * persistidos junto com o polígono importado.
 */
@Schema(name = "PublicBeach", description = "Área de banho visível no mapa público")
public record BeachSummary(
        @Schema(description = "Identificador estável da área") UUID id,
        @Schema(description = "Nome da praia, rio ou barragem", example = "Praia da Orla") String name,
        @Schema(description = "Tipo da área de banho", example = "BEACH") BeachKind kind,
        @Schema(description = "Município da área", example = "Aracaju") String municipality,
        @Schema(description = "UF da área", example = "SE") String stateCode,
        @Schema(description = "Quantidade de setores ativos", example = "2") int sectorCount,
        @Schema(description = "Latitude do centro da área", example = "-10.985") double latitude,
        @Schema(description = "Longitude do centro da área", example = "-37.045") double longitude,
        @Schema(description = "Distância em metros até a área, medida a partir de quem consultou",
                example = "4200") long distanceMeters) {
}
