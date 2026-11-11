package com.maraberto.emergency.application;

import com.maraberto.emergency.api.EmergencyCallNotification;
import com.maraberto.realtime.api.SosNotifierPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa os destinatários perdedores do ack para retirarem o alerta da tela.
 *
 * <p>É um aviso de melhor esforço, enviado após o commit: a verdade do
 * cancelamento está no banco e o console reconcilia por HTTP.
 */
@Component
public class SosCancellationNotifier {

    private final SosNotifierPort sosNotifier;

    public SosCancellationNotifier(SosNotifierPort sosNotifier) {
        this.sosNotifier = sosNotifier;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSosAcknowledged(SosAcknowledged event) {
        event.cancelledRecipients().forEach(recipient -> sosNotifier.send(
                recipient.userId(),
                new EmergencyCallNotification(
                        event.callId(),
                        recipient.dispatchId(),
                        recipient.round(),
                        "CANCELLED",
                        event.occurredAt())));
    }
}
