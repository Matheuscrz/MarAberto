package com.maraberto.realtime.infra;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Trata falhas de consumo nas filas de SOS: reencaminha para a fila de retry
 * (com TTL) até {@code maxRetries} e, esgotadas as tentativas, envia para a DLQ.
 */
@Component
@ConditionalOnProperty(
        prefix = "maraberto.realtime.rabbit",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SosAmqpRetryHelper {

    private static final Logger log = LoggerFactory.getLogger(SosAmqpRetryHelper.class);

    private final RabbitTemplate rabbitTemplate;
    private final int maxRetries;

    public SosAmqpRetryHelper(
            RabbitTemplate rabbitTemplate,
            @Value("${maraberto.realtime.rabbit.max-retries:3}") int maxRetries) {
        this.rabbitTemplate = rabbitTemplate;
        this.maxRetries = maxRetries;
    }

    public void handleFailure(
            String queueName,
            String dlqRoutingKey,
            Message rawMessage,
            Channel channel,
            long deliveryTag,
            Exception cause) throws IOException {
        long retries = countRetries(queueName, rawMessage.getMessageProperties().getHeader("x-death"));
        if (retries < maxRetries) {
            log.warn("Falha no consumidor {} (tentativa {}/{}), enviando para retry com TTL: {}",
                    queueName, retries + 1, maxRetries, cause.getClass().getSimpleName());
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        log.error("Tentativas esgotadas em {} ({}/{}), movendo mensagem {} para DLQ {}",
                queueName, retries, maxRetries,
                rawMessage.getMessageProperties().getMessageId(), dlqRoutingKey);
        rabbitTemplate.send(RabbitSosTopologyConfig.EXCHANGE_DLX, dlqRoutingKey, rawMessage);
        channel.basicAck(deliveryTag, false);
    }

    static long countRetries(String queueName, Object xDeathHeader) {
        if (!(xDeathHeader instanceof List<?> deaths)) {
            return 0;
        }
        long total = 0;
        for (Object entry : deaths) {
            if (entry instanceof Map<?, ?> map && queueName.equals(map.get("queue"))) {
                Object count = map.get("count");
                if (count instanceof Number number) {
                    total += number.longValue();
                }
            }
        }
        return total;
    }
}
