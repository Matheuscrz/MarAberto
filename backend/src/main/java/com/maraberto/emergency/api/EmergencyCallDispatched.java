package com.maraberto.emergency.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento durável de despacho do SOS para um destinatário.
 *
 * <p>O destinatário já foi escolhido pelo banco (ADR-018); o evento leva apenas
 * identificadores — sem coordenadas e sem {@code clientRequestId}, que ficam no
 * chamado. O {@code dispatchId} é a chave de idempotência da entrega.
 */
@Schema(name = "EmergencyCallDispatched",
        description = "Evento persistido após o despacho de um SOS para um destinatário")
public record EmergencyCallDispatched(
        @Schema(description = "Identificador da chamada de emergência")
        UUID callId,
        @Schema(description = "Identificador do despacho (chave de idempotência da entrega)")
        UUID dispatchId,
        @Schema(description = "Operador que deve receber a notificação")
        UUID recipientUserId,
        @Schema(description = "Posto fixo do destinatário, quando houver")
        UUID postId,
        @Schema(description = "Base móvel do destinatário, quando houver")
        UUID baseId,
        @Schema(description = "Rodada de despacho", example = "1")
        int round,
        @Schema(description = "Momento do despacho, em UTC")
        Instant occurredAt) {
}
