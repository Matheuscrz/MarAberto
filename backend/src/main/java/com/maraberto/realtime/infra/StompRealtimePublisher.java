package com.maraberto.realtime.infra;

import com.maraberto.realtime.api.RealtimePublisherPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica mensagens no broker STOMP simples configurado em
 * {@code WebSocketConfig}.
 *
 * <p>A publicação é em memória: sem assinantes ela é descartada. Por isso a
 * outbox marca o evento como publicado somente depois que o envio ao broker
 * retorna sem erro — e o console operacional reconcilia por HTTP ao
 * reconectar.
 */
@Component
public class StompRealtimePublisher implements RealtimePublisherPort {

    private final SimpMessagingTemplate messagingTemplate;

    public StompRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }
}
