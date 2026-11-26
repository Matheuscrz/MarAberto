package com.maraberto.realtime.infra;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.messaging.handler.annotation.Header;

/**
 * Consumidor AMQP isolado para envio de notificações push (Web Push VAPID e FCM).
 */
@Component
@Profile("rabbit")
public class SosPushAmqpConsumer {

    private final SosPushDispatcher pushDispatcher;
    private final SosAmqpRetryHelper retryHelper;

    public SosPushAmqpConsumer(
            SosPushDispatcher pushDispatcher,
            SosAmqpRetryHelper retryHelper) {
        this.pushDispatcher = pushDispatcher;
        this.retryHelper = retryHelper;
    }

    @RabbitListener(queues = RabbitSosTopologyConfig.QUEUE_PUSH, ackMode = "MANUAL")
    public void onMessage(
            SosAmqpMessage message,
            Message rawMessage,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            pushDispatcher.dispatch(message.userId(), message.payload());
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            retryHelper.handleFailure(
                    RabbitSosTopologyConfig.QUEUE_PUSH,
                    RabbitSosTopologyConfig.QUEUE_PUSH_DLQ,
                    rawMessage,
                    channel,
                    deliveryTag,
                    exception);
        }
    }
}
