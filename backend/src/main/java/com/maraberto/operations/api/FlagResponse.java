package com.maraberto.operations.api;

import com.maraberto.operations.domain.FlagColor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Resultado do lançamento ou encerramento de sinalização (console do operador). */
@Schema(name = "FlagResponse", description = "Sinalização lançada ou encerrada")
public record FlagResponse(
        UUID id,
        UUID beachId,
        UUID sectorId,
        FlagColor flag,
        Instant createdAt,
        Instant validUntil,
        Instant closedAt,
        @Schema(description = "Fotos anexadas ao lançamento") List<String> photoUrls) {

    public FlagResponse {
        photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
    }
}
