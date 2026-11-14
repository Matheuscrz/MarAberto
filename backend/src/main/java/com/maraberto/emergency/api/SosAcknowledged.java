package com.maraberto.emergency.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Disparado dentro da transação do ack; a notificação de cancelamento aos
 * perdedores e a abertura da ocorrência operacional (ADR-013) só acontecem
 * depois do commit.
 *
 * <p>É a interface pública do ack: o módulo {@code occurrences} escuta este
 * evento para abrir a ficha do atendimento sem que o SOS espere por ela.
 */
public record SosAcknowledged(
        UUID callId,
        Instant occurredAt,
        List<CancelledRecipient> cancelledRecipients) {

    public record CancelledRecipient(UUID userId, UUID dispatchId, int round) {
    }
}
