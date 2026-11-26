package com.maraberto.realtime.infra;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/**
 * Envelope publicado no RabbitMQ pelo {@link AmqpSosNotifier}.
 * Contém apenas o identificador do operador destinatário e o payload redigido.
 */
public record SosAmqpMessage(
        UUID userId,
        JsonNode payload) {
}
