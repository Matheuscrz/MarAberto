package com.maraberto.realtime.infra;

import com.maraberto.realtime.api.SosDestinations;
import com.maraberto.realtime.api.SosNotifierPort;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Entrega o SOS na fila pessoal do operador pelo broker STOMP em memória.
 *
 * <p>Sem sessão conectada a mensagem é descartada. Por isso o console reconcilia
 * por HTTP ({@code GET /api/v1/dispatch/calls}) e a outbox mantém o evento até
 * a publicação ser confirmada pelo adaptador.
 */
@Component
public class StompSosNotifier implements SosNotifierPort {

    private final SimpMessagingTemplate messagingTemplate;

    public StompSosNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void send(UUID userId, Object payload) {
        messagingTemplate.convertAndSendToUser(userId.toString(), SosDestinations.SOS_QUEUE, payload);
    }
}
