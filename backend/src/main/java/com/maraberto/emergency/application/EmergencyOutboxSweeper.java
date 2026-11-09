package com.maraberto.emergency.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recupera eventos da outbox que ficaram pendentes (queda da aplicação,
 * broker indisponível, publicação imediata que falhou).
 *
 * <p>O intervalo curto é intencional: o SOS tem p95 &lt; 3 s. O sweep é
 * barato quando não há eventos — a consulta usa índice parcial por
 * {@code status = 'PENDING'}.
 */
@Component
public class EmergencyOutboxSweeper {

    private final EmergencyOutboxPublisher publisher;

    public EmergencyOutboxSweeper(EmergencyOutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(
            fixedDelayString = "${maraberto.emergency.outbox.sweep-interval:PT5S}",
            initialDelayString = "${maraberto.emergency.outbox.sweep-initial-delay:PT10S}")
    public void sweep() {
        publisher.publishPending();
    }
}
