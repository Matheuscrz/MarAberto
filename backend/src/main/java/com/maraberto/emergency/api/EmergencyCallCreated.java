package com.maraberto.emergency.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento durável emitido após a criação de um SOS.
 */
public record EmergencyCallCreated(
        UUID callId,
        String clientRequestId,
        double latitude,
        double longitude,
        Double accuracyMeters,
        Instant occurredAt) {
}
