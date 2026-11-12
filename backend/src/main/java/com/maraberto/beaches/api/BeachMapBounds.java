package com.maraberto.beaches.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Retângulo que contém uma geometria do mapa, em WGS 84.
 *
 * <p>É o que o cliente precisa para enquadrar o mapa sem recalcular o polígono:
 * o {@code bbox} de um setor é mais preciso que o da área, que é a união de
 * todos os setores.
 */
@Schema(name = "BeachMapBounds", description = "Retângulo que contém a geometria, em WGS 84")
public record BeachMapBounds(
        @Schema(description = "Longitude mínima (oeste)", example = "-37.0521") double minLon,
        @Schema(description = "Latitude mínima (sul)", example = "-10.9912") double minLat,
        @Schema(description = "Longitude máxima (leste)", example = "-37.0402") double maxLon,
        @Schema(description = "Latitude máxima (norte)", example = "-10.9803") double maxLat) {

    /** Menor longitude, menor latitude, maior longitude e maior latitude. */
    public double[] asArray() {
        return new double[] {minLon, minLat, maxLon, maxLat};
    }
}
