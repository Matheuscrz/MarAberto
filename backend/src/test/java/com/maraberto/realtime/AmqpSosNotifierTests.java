package com.maraberto.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.realtime.infra.AmqpSosNotifier;
import com.maraberto.realtime.infra.RabbitSosTopologyConfig;
import com.maraberto.realtime.infra.SosAmqpMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class AmqpSosNotifierTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void routesDispatchedToNotifyAndCancelledToCancelWithConfirm() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AmqpSosNotifier notifier = new AmqpSosNotifier(rabbitTemplate, objectMapper, 1000);

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(4);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                any(String.class), any(String.class), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

        UUID userId = UUID.randomUUID();
        notifier.send(userId, Map.of(
                "callId", UUID.randomUUID(),
                "dispatchId", UUID.randomUUID(),
                "status", "DISPATCHED",
                "occurredAt", Instant.now()));
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitSosTopologyConfig.EXCHANGE_EMERGENCY),
                eq(RabbitSosTopologyConfig.ROUTING_KEY_NOTIFY),
                any(SosAmqpMessage.class),
                any(MessagePostProcessor.class),
                any(CorrelationData.class));

        notifier.send(userId, Map.of(
                "callId", UUID.randomUUID(),
                "dispatchId", UUID.randomUUID(),
                "status", "CANCELLED",
                "occurredAt", Instant.now()));
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitSosTopologyConfig.EXCHANGE_EMERGENCY),
                eq(RabbitSosTopologyConfig.ROUTING_KEY_CANCEL),
                any(SosAmqpMessage.class),
                any(MessagePostProcessor.class),
                any(CorrelationData.class));
    }

    @Test
    void throwsOnBrokerNackSoOutboxKeepsEventPending() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AmqpSosNotifier notifier = new AmqpSosNotifier(rabbitTemplate, objectMapper, 1000);

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(4);
            cd.getFuture().complete(new CorrelationData.Confirm(false, "disk full"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                any(String.class), any(String.class), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

        assertThatThrownBy(() -> notifier.send(UUID.randomUUID(), Map.of("status", "DISPATCHED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disk full");
    }

    @Test
    void throwsWhenMessageIsReturnedAsUnroutable() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ArgumentCaptor<RabbitTemplate.ReturnsCallback> returnsCaptor =
                ArgumentCaptor.forClass(RabbitTemplate.ReturnsCallback.class);
        AmqpSosNotifier notifier = new AmqpSosNotifier(rabbitTemplate, objectMapper, 1000);
        verify(rabbitTemplate).setReturnsCallback(returnsCaptor.capture());

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(4);
            MessageProperties props = new MessageProperties();
            props.setCorrelationId(cd.getId());
            returnsCaptor.getValue().returnedMessage(new ReturnedMessage(
                    new Message("{}".getBytes(StandardCharsets.UTF_8), props),
                    312,
                    "NO_ROUTE",
                    RabbitSosTopologyConfig.EXCHANGE_EMERGENCY,
                    RabbitSosTopologyConfig.ROUTING_KEY_NOTIFY));
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                any(String.class), any(String.class), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

        assertThatThrownBy(() -> notifier.send(UUID.randomUUID(), Map.of("status", "DISPATCHED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NO_ROUTE");
        assertThat(notifier).isNotNull();
    }
}
