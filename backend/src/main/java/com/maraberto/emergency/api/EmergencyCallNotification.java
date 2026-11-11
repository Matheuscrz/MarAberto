package com.maraberto.emergency.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Notificação operacional de um SOS, entregue em fila pessoal
 * ({@code /user/{userId}/queue/sos}).
 *
 * <p><strong>Payload redigido por decisão de segurança:</strong> não contém
 * coordenadas, {@code clientRequestId} nem qualquer dado pessoal. O console
 * busca o detalhe em rota autenticada usando o {@code callId}. O
 * {@code dispatchId} permite descartar duplicata da entrega at-least-once.
 */
@Schema(name = "EmergencyCallNotification",
        description = "Notificação de SOS sem dados pessoais, entregue por WebSocket/STOMP")
public record EmergencyCallNotification(
        @Schema(description = "Identificador da chamada de emergência")
        UUID callId,
        @Schema(description = "Identificador do despacho")
        UUID dispatchId,
        @Schema(description = "Rodada de despacho", example = "1")
        int round,
        @Schema(description = "Estado do chamado no momento da notificação", example = "DISPATCHED")
        String status,
        @Schema(description = "Momento em que o evento ocorreu, em UTC")
        Instant occurredAt) {
}
