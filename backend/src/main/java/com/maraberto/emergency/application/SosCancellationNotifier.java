package com.maraberto.emergency.application;

import com.maraberto.emergency.api.EmergencyCallNotification;
import com.maraberto.emergency.api.SosAcknowledged;
import com.maraberto.realtime.api.SosNotifierPort;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa os destinatários perdedores do ack para retirarem o alerta da tela.
 *
 * <p>É um aviso de melhor esforço, enviado após o commit: a verdade do
 * cancelamento está no banco e o console reconcilia por HTTP. Falhas no broker
 * nunca podem quebrar o ack nem bloquear a resposta HTTP.
 */
@Component
public class SosCancellationNotifier {

    private static final Logger log = LoggerFactory.getLogger(SosCancellationNotifier.class);

    private final SosNotifierPort sosNotifier;
    private final boolean asyncDispatch;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SosCancellationNotifier(
            SosNotifierPort sosNotifier,
            @Value("${maraberto.emergency.outbox.async-dispatch:true}") boolean asyncDispatch) {
        this.sosNotifier = sosNotifier;
        this.asyncDispatch = asyncDispatch;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSosAcknowledged(SosAcknowledged event) {
        if (!asyncDispatch) {
            deliverCancellations(event);
            return;
        }
        try {
            executor.execute(() -> deliverCancellations(event));
        } catch (RejectedExecutionException exception) {
            log.debug("Executor de cancelamento encerrado para chamado {}", event.callId());
        }
    }

    private void deliverCancellations(SosAcknowledged event) {
        event.cancelledRecipients().forEach(recipient -> {
            try {
                sosNotifier.send(
                        recipient.userId(),
                        new EmergencyCallNotification(
                                event.callId(),
                                recipient.dispatchId(),
                                recipient.round(),
                                "CANCELLED",
                                event.occurredAt()));
            } catch (RuntimeException exception) {
                log.warn("Falha best-effort ao notificar cancelamento do despacho {}: {}",
                        recipient.dispatchId(), exception.getClass().getSimpleName());
            }
        });
    }
}
