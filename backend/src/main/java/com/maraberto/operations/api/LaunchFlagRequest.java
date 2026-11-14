package com.maraberto.operations.api;

import com.maraberto.operations.domain.FlagColor;
import com.maraberto.shared.security.sanitization.SanitizationPolicy;
import com.maraberto.shared.security.sanitization.Sanitized;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Lançamento de bandeira por um guarda-vidas autenticado (RF12).
 *
 * <p>Duas formas de dizer **onde** a bandeira está sendo plantada:
 * <ul>
 *   <li>escopo no console — {@code beachId} e, opcionalmente, {@code sectorId};
 *   <li>localização do aparelho — {@code latitude}/{@code longitude}; o
 *       servidor resolve a praia e o setor pelo ponto.
 * </ul>
 *
 * <p>Sem setor, a bandeira vale para a praia inteira. Com setor, vale só para
 * ele — e um setor sem lançamento próprio herda a bandeira da praia na ficha.
 * A foto opcional é do lançamento: ela aparece na ficha da praia e no mapa,
 * nunca em listagem pública sem a bandeira vigente.
 *
 * @param beachId        área de banho sinalizada; omitida no modo localização
 * @param sectorId       setor específico; nulo para a praia inteira
 * @param flag           cor lançada
 * @param notes          observação curta, exibida na ficha
 * @param validityHours  validade em horas; nulo usa o padrão da instalação (RN01)
 * @param latitude       latitude do aparelho no modo localização
 * @param longitude      longitude do aparelho no modo localização
 * @param accuracyMeters precisão informada pelo aparelho, quando houver
 * @param mediaIds       fotos já enviadas por {@code POST /api/v1/media} (até 3)
 */
@Schema(name = "LaunchFlagRequest", description = "Novo lançamento de bandeira")
public record LaunchFlagRequest(
        @Schema(description = "Área de banho sinalizada; omitida no modo localização")
        UUID beachId,
        @Schema(description = "Setor específico; omitido, vale para a praia inteira")
        UUID sectorId,
        @Schema(description = "Cor da bandeira", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull FlagColor flag,
        @Schema(description = "Observação exibida na ficha da sinalização", maxLength = 400)
        @Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 400)
        @Size(max = 400) String notes,
        @Schema(description = "Validade em horas (padrão 12, máximo 24)", example = "12")
        @DecimalMin("0.5") @DecimalMax("24.0") Double validityHours,
        @Schema(description = "Latitude atual do aparelho (modo localização)", example = "-10.996")
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @Schema(description = "Longitude atual do aparelho (modo localização)", example = "-37.045")
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Schema(description = "Precisão em metros informada pelo aparelho", example = "12")
        @DecimalMin("0.0") @DecimalMax("100000.0") Double accuracyMeters,
        @Schema(description = "Fotos da sinalização, na ordem de exibição")
        @Size(max = 3) List<UUID> mediaIds) {

    /** Lançamento ancorado no ponto do aparelho. */
    public boolean hasPosition() {
        return latitude != null && longitude != null;
    }
}
