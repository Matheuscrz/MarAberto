package com.maraberto.emergency.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento durável emitido após a criação de um SOS.
 */
@Schema(name = "EmergencyCallCreated", description = "Evento persistido após a criação de um SOS")
public record EmergencyCallCreated(
        @Schema(description = "Identificador da chamada de emergência")
        UUID callId,
        @Schema(description = "Identificador idempotente recebido do cliente")
        String clientRequestId,
        @Schema(description = "Latitude WGS84 do SOS")
        double latitude,
        @Schema(description = "Longitude WGS84 do SOS")
        double longitude,
        @Schema(description = "Precisão estimada da localização, em metros")
        Double accuracyMeters,
        @Schema(description = "Momento em que o evento foi criado, em UTC")
        Instant occurredAt) {
}
