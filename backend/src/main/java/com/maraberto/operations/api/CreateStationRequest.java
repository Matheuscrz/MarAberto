package com.maraberto.operations.api;

import com.maraberto.operations.domain.StationKind;
import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Cadastro de posto fixo ou base móvel com a posição atual do aparelho (RF11).
 *
 * <p>O guarda-vidas cadastra o posto/base onde ele está agora: a coordenada
 * vem do GPS do aparelho e o servidor guarda a origem e a precisão para a
 * coordenação julgar o ponto. Posto fixo precisa da área de banho; base móvel
 * não pertence a uma praia.
 *
 * @param kind            posto fixo ou base móvel
 * @param name            nome curto exibido no mapa
 * @param beachId         área de banho do posto fixo; nulo para base móvel
 * @param latitude        latitude atual do aparelho
 * @param longitude       longitude atual do aparelho
 * @param accuracyMeters  precisão informada pelo aparelho, quando houver
 * @param mediaIds        fotos do posto/base já enviadas pelo mesmo operador (até 3)
 */
@Schema(name = "CreateStationRequest", description = "Novo posto ou base na posição atual")
public record CreateStationRequest(
        @Schema(description = "Tipo da estrutura", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull StationKind kind,
        @Schema(description = "Nome exibido no mapa", requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 120)
        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 120)
        @NotBlank @Size(max = 120) String name,
        @Schema(description = "Área de banho do posto fixo")
        UUID beachId,
        @Schema(description = "Latitude atual do aparelho", example = "-10.996",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @Schema(description = "Longitude atual do aparelho", example = "-37.045",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Schema(description = "Precisão em metros informada pelo aparelho", example = "12")
        @DecimalMin("0.0") @DecimalMax("100000.0") Double accuracyMeters,
        @Schema(description = "Fotos do posto/base enviadas pelo mesmo operador (até 3)")
        @Size(max = 3) List<UUID> mediaIds) {
}
