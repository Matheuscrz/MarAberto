package com.maraberto.beaches.api;

import com.maraberto.beaches.domain.BeachKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Ficha pública da área de banho com os setores ativos.
 */
@Schema(name = "PublicBeachDetail", description = "Área de banho com os setores ativos")
public record BeachDetail(
        @Schema(description = "Identificador estável da área") UUID id,
        @Schema(description = "Nome da praia, rio ou barragem") String name,
        @Schema(description = "Tipo da área de banho") BeachKind kind,
        @Schema(description = "Município da área") String municipality,
        @Schema(description = "UF da área") String stateCode,
        @Schema(description = "Latitude do centro da área") double latitude,
        @Schema(description = "Longitude do centro da área") double longitude,
        @Schema(description = "Setores ativos, na ordem do KML") List<BeachSectorSummary> sectors) {
}
