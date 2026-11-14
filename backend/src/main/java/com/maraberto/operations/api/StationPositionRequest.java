package com.maraberto.operations.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Reposição da estrutura pela posição atual do aparelho (RF11).
 *
 * <p>Mudar a posição não apaga o histórico: plantões e ocorrências continuam
 * apontando para a mesma estrutura, e o painel passa a mostrar a origem
 * {@code DEVICE_GPS}.
 *
 * @param latitude       latitude atual do aparelho
 * @param longitude      longitude atual do aparelho
 * @param accuracyMeters precisão informada pelo aparelho, quando houver
 */
@Schema(name = "StationPositionRequest", description = "Nova posição da estrutura")
public record StationPositionRequest(
        @Schema(description = "Latitude atual do aparelho", example = "-10.996",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @Schema(description = "Longitude atual do aparelho", example = "-37.045",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Schema(description = "Precisão em metros informada pelo aparelho", example = "12")
        @DecimalMin("0.0") @DecimalMax("100000.0") Double accuracyMeters) {
}
