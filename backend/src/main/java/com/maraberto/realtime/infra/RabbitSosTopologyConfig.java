package com.maraberto.realtime.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Topologia RabbitMQ para entrega isolada de SOS (STOMP + Push) com retry via
 * TTL e DLQ (ADR-004, ADR-018, Architecture.md §11.3).
 */
@Configuration
@Profile("rabbit")
public class RabbitSosTopologyConfig {

    public static final String EXCHANGE_EMERGENCY = "maraberto.emergency";
    public static final String EXCHANGE_DLX = "maraberto.emergency.dlx";

    public static final String ROUTING_KEY_NOTIFY = "sos.notify";
    public static final String ROUTING_KEY_CANCEL = "sos.cancel";

    public static final String QUEUE_REALTIME = "q.sos.realtime";
    public static final String QUEUE_REALTIME_RETRY = "q.sos.realtime.retry";
    public static final String QUEUE_REALTIME_DLQ = "q.sos.realtime.dlq";

    public static final String QUEUE_PUSH = "q.sos.push";
    public static final String QUEUE_PUSH_RETRY = "q.sos.push.retry";
    public static final String QUEUE_PUSH_DLQ = "q.sos.push.dlq";

    @Bean
    TopicExchange emergencyExchange() {
        return new TopicExchange(EXCHANGE_EMERGENCY, true, false);
    }

    @Bean
    DirectExchange emergencyDlxExchange() {
        return new DirectExchange(EXCHANGE_DLX, true, false);
    }

    @Bean
    Queue realtimeQueue() {
        return QueueBuilder.durable(QUEUE_REALTIME)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(QUEUE_REALTIME_RETRY)
                .build();
    }

    @Bean
    Queue realtimeRetryQueue(
            @Value("${maraberto.realtime.rabbit.retry-ttl-ms:5000}") long retryTtlMs) {
        return QueueBuilder.durable(QUEUE_REALTIME_RETRY)
                .ttl(Math.toIntExact(retryTtlMs))
                .deadLetterExchange("")
                .deadLetterRoutingKey(QUEUE_REALTIME)
                .build();
    }

    @Bean
    Queue realtimeDlq() {
        return QueueBuilder.durable(QUEUE_REALTIME_DLQ).build();
    }

    @Bean
    Binding realtimeNotifyBinding(Queue realtimeQueue, TopicExchange emergencyExchange) {
        return BindingBuilder.bind(realtimeQueue).to(emergencyExchange).with(ROUTING_KEY_NOTIFY);
    }

    @Bean
    Binding realtimeCancelBinding(Queue realtimeQueue, TopicExchange emergencyExchange) {
        return BindingBuilder.bind(realtimeQueue).to(emergencyExchange).with(ROUTING_KEY_CANCEL);
    }

    @Bean
    Binding realtimeRetryBinding(Queue realtimeRetryQueue, DirectExchange emergencyDlxExchange) {
        return BindingBuilder.bind(realtimeRetryQueue).to(emergencyDlxExchange).with(QUEUE_REALTIME_RETRY);
    }

    @Bean
    Binding realtimeDlqBinding(Queue realtimeDlq, DirectExchange emergencyDlxExchange) {
        return BindingBuilder.bind(realtimeDlq).to(emergencyDlxExchange).with(QUEUE_REALTIME_DLQ);
    }

    @Bean
    Queue pushQueue() {
        return QueueBuilder.durable(QUEUE_PUSH)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(QUEUE_PUSH_RETRY)
                .build();
    }

    @Bean
    Queue pushRetryQueue(
            @Value("${maraberto.realtime.rabbit.retry-ttl-ms:5000}") long retryTtlMs) {
        return QueueBuilder.durable(QUEUE_PUSH_RETRY)
                .ttl(Math.toIntExact(retryTtlMs))
                .deadLetterExchange("")
                .deadLetterRoutingKey(QUEUE_PUSH)
                .build();
    }

    @Bean
    Queue pushDlq() {
        return QueueBuilder.durable(QUEUE_PUSH_DLQ).build();
    }

    @Bean
    Binding pushNotifyBinding(Queue pushQueue, TopicExchange emergencyExchange) {
        return BindingBuilder.bind(pushQueue).to(emergencyExchange).with(ROUTING_KEY_NOTIFY);
    }

    @Bean
    Binding pushCancelBinding(Queue pushQueue, TopicExchange emergencyExchange) {
        return BindingBuilder.bind(pushQueue).to(emergencyExchange).with(ROUTING_KEY_CANCEL);
    }

    @Bean
    Binding pushRetryBinding(Queue pushRetryQueue, DirectExchange emergencyDlxExchange) {
        return BindingBuilder.bind(pushRetryQueue).to(emergencyDlxExchange).with(QUEUE_PUSH_RETRY);
    }

    @Bean
    Binding pushDlqBinding(Queue pushDlq, DirectExchange emergencyDlxExchange) {
        return BindingBuilder.bind(pushDlq).to(emergencyDlxExchange).with(QUEUE_PUSH_DLQ);
    }

    @Bean
    MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
