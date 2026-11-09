package com.maraberto.emergency.application;

import java.util.UUID;

/**
 * Evento interno (Spring, em processo) disparado após o commit do SOS para
 * acordar o publicador da outbox sem esperar o próximo ciclo do sweep.
 *
 * <p>Carrega apenas o identificador do evento durável — nunca o payload, que
 * está cifrado no banco.
 */
public record EmergencyOutboxEventQueued(UUID outboxEventId) {
}
