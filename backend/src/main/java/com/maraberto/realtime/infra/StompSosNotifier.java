package com.maraberto.realtime.infra;

import com.maraberto.realtime.api.SosDestinations;
import com.maraberto.realtime.api.SosNotifierPort;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Entrega o SOS na fila pessoal do operador pelo broker STOMP em memória
 * (perfil mínimo sem RabbitMQ), com envio best-effort de Web Push/FCM.
 *
 * <p>Sem sessão conectada a mensagem STOMP é descartada. Por isso o console
 * reconcilia por HTTP ({@code GET /api/v1/dispatch/calls}) e a outbox mantém
 * o evento até a publicação ser confirmada pelo adaptador.
 */
@Component
@Profile("!rabbit")
public class StompSosNotifier implements SosNotifierPort {

    private final SimpMessagingTemplate messagingTemplate;
    private final SosPushDispatcher pushDispatcher;

    public StompSosNotifier(
            SimpMessagingTemplate messagingTemplate,
            SosPushDispatcher pushDispatcher) {
        this.messagingTemplate = messagingTemplate;
        this.pushDispatcher = pushDispatcher;
    }

    @Override
    public void send(UUID userId, Object payload) {
        messagingTemplate.convertAndSendToUser(userId.toString(), SosDestinations.SOS_QUEUE, payload);
        pushDispatcher.dispatchBestEffort(userId, payload);
    }
}
