package com.maraberto.realtime.infra;

import com.maraberto.realtime.api.SosDestinations;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumidor AMQP que entrega o alerta do SOS na fila pessoal STOMP do operador.
 */
@Component
@Profile("rabbit")
public class SosRealtimeAmqpConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final SosAmqpRetryHelper retryHelper;

    public SosRealtimeAmqpConsumer(
            SimpMessagingTemplate messagingTemplate,
            SosAmqpRetryHelper retryHelper) {
        this.messagingTemplate = messagingTemplate;
        this.retryHelper = retryHelper;
    }

    @RabbitListener(queues = RabbitSosTopologyConfig.QUEUE_REALTIME, ackMode = "MANUAL")
    public void onMessage(
            SosAmqpMessage message,
            Message rawMessage,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            messagingTemplate.convertAndSendToUser(
                    message.userId().toString(),
                    SosDestinations.SOS_QUEUE,
                    message.payload());
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            retryHelper.handleFailure(
                    RabbitSosTopologyConfig.QUEUE_REALTIME,
                    RabbitSosTopologyConfig.QUEUE_REALTIME_DLQ,
                    rawMessage,
                    channel,
                    deliveryTag,
                    exception);
        }
    }
}
