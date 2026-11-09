package com.maraberto.emergency.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Notificação operacional de um SOS, publicada por WebSocket/STOMP.
 *
 * <p><strong>Payload redigido por decisão de segurança:</strong> não contém
 * coordenadas, {@code clientRequestId} nem qualquer dado pessoal, porque o
 * destino {@code /topic/**} ainda não tem autorização por papel. O console
 * operacional usa o {@code callId} para buscar o detalhe em uma rota
 * autenticada.
 */
@Schema(name = "EmergencyCallNotification",
        description = "Notificação operacional de SOS sem dados pessoais, entregue por WebSocket/STOMP")
public record EmergencyCallNotification(
        @Schema(description = "Identificador da chamada de emergência")
        UUID callId,
        @Schema(description = "Estado do chamado no momento da notificação", example = "DISPATCHED")
        String status,
        @Schema(description = "Momento em que o SOS foi registrado, em UTC")
        Instant occurredAt) {
}
