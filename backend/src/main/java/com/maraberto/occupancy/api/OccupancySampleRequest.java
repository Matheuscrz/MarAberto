package com.maraberto.occupancy.api;

import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Amostra anônima de presença na praia.
 *
 * <p>A coordenada serve só para o servidor descobrir o setor e é descartada em
 * seguida; o token é um identificador efêmero gerado pelo cliente, guardado
 * apenas como HMAC e usado para não contar o mesmo aparelho duas vezes na mesma
 * janela.
 */
@Schema(name = "OccupancySample", description = "Amostra anônima de presença em uma área de banho")
public record OccupancySampleRequest(
        @Schema(description = "Latitude atual", example = "-10.985")
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @Schema(description = "Longitude atual", example = "-37.045")
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Schema(description = "Token efêmero do cliente; nunca é persistido em claro")
        @NotBlank @Size(min = 16, max = 64) @Pattern(regexp = "[A-Za-z0-9-]+")
        @Sanitized(value = SanitizationPolicy.IDENTIFIER, maxLength = 64) String token) {
}
