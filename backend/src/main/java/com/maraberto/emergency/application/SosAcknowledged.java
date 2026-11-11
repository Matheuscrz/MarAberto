package com.maraberto.emergency.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Disparado dentro da transação do ack; a notificação de cancelamento aos
 * perdedores só acontece depois do commit.
 */
public record SosAcknowledged(
        UUID callId,
        Instant occurredAt,
        List<CancelledRecipient> cancelledRecipients) {

    public record CancelledRecipient(UUID userId, UUID dispatchId, int round) {
    }
}
