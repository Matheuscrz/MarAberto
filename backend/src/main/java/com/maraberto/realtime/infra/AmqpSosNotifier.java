package com.maraberto.realtime.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.realtime.api.SosNotifierPort;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador AMQP (padrão quando {@code maraberto.realtime.rabbit.enabled=true})
 * para publicação confiável do SOS com publisher confirms + {@code mandatory}
 * returns (ADR-004, ADR-018).
 *
 * <p>Se o broker estiver fora, retornar NACK, devolver a mensagem como não
 * roteável ou estourar o timeout do confirm, lança exceção para que a
 * Transactional Outbox mantenha o evento {@code PENDING} no banco e o sweep
 * reenvie assim que o broker voltar.
 */
@Component
@ConditionalOnProperty(
        prefix = "maraberto.realtime.rabbit",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AmqpSosNotifier implements SosNotifierPort {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final long confirmTimeoutMs;
    private final ConcurrentHashMap<String, ReturnedMessage> returnedMessages = new ConcurrentHashMap<>();

    public AmqpSosNotifier(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${maraberto.realtime.rabbit.confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.confirmTimeoutMs = confirmTimeoutMs;
        this.rabbitTemplate.setMandatory(true);
        this.rabbitTemplate.setReturnsCallback(returned -> {
            String correlationId = returned.getMessage().getMessageProperties().getCorrelationId();
            if (correlationId != null) {
                returnedMessages.put(correlationId, returned);
            }
        });
    }

    @Override
    public void send(UUID userId, Object payload) {
        JsonNode jsonPayload = objectMapper.valueToTree(payload);
        String status = jsonPayload.path("status").asText("DISPATCHED");
        String routingKey = "CANCELLED".equals(status)
                ? RabbitSosTopologyConfig.ROUTING_KEY_CANCEL
                : RabbitSosTopologyConfig.ROUTING_KEY_NOTIFY;
        String dispatchId = jsonPayload.path("dispatchId").asText("");
        String messageId = dispatchId.isBlank() ? UUID.randomUUID().toString() : dispatchId;

        String correlationId = UUID.randomUUID().toString();
        CorrelationData correlationData = new CorrelationData(correlationId);
        SosAmqpMessage envelope = new SosAmqpMessage(userId, jsonPayload);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitSosTopologyConfig.EXCHANGE_EMERGENCY,
                    routingKey,
                    envelope,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setMessageId(messageId);
                        message.getMessageProperties().setCorrelationId(correlationId);
                        return message;
                    },
                    correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            ReturnedMessage returned = returnedMessages.remove(correlationId);
            if (returned != null) {
                throw new IllegalStateException(
                        "Mensagem SOS retornada como não roteável: " + returned.getReplyText());
            }
            if (confirm == null || !confirm.isAck()) {
                String reason = confirm == null ? "confirm nulo" : confirm.getReason();
                throw new IllegalStateException("Broker rejeitou publicação do SOS: " + reason);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            returnedMessages.remove(correlationId);
            throw new IllegalStateException("Thread interrompida aguardando confirm do RabbitMQ", exception);
        } catch (ExecutionException | TimeoutException exception) {
            returnedMessages.remove(correlationId);
            throw new IllegalStateException("Falha aguardando confirm do RabbitMQ", exception);
        }
    }
}
