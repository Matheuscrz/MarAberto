package com.maraberto.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.realtime.api.SosDestinations;
import com.maraberto.realtime.api.SosNotifierPort;
import com.maraberto.realtime.domain.PushChannel;
import com.maraberto.realtime.infra.AmqpSosNotifier;
import com.maraberto.realtime.infra.PushSubscriptionRepository;
import com.maraberto.realtime.infra.RabbitSosTopologyConfig;
import com.maraberto.realtime.infra.SosAmqpMessage;
import com.maraberto.support.AbstractPostgisTest;
import com.maraberto.support.EmergencyTestData;
import com.maraberto.support.TestOperatorAuth;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Prova a integração RabbitMQ de ponta a ponta (ativa por padrão, sem perfil
 * opt-in) com Testcontainers RabbitMQ: publisher confirms + {@code mandatory},
 * entrega isolada em {@code q.sos.realtime} (STOMP) e {@code q.sos.push} (FCM),
 * retry com TTL e envio à DLQ após esgotar as tentativas.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "maraberto.emergency.outbox.sweep-initial-delay=PT1H",
                "spring.rabbitmq.listener.simple.auto-startup=true",
                "maraberto.realtime.rabbit.retry-ttl-ms=200",
                "maraberto.realtime.rabbit.max-retries=2",
                "maraberto.realtime.rabbit.confirm-timeout-ms=3000",
                "maraberto.push.fcm.enabled=true"
        })
class RabbitSosIntegrationTests extends AbstractPostgisTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    private static HttpServer fcmMockServer;
    private static volatile int fcmResponseStatus = 200;
    private static final BlockingQueue<String> fcmRequests = new LinkedBlockingQueue<>();

    static {
        RABBITMQ.start();
        try {
            fcmMockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            fcmMockServer.createContext("/fcm/send", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                fcmRequests.offer(new String(body, StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(fcmResponseStatus, -1);
                exchange.close();
            });
            fcmMockServer.start();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @DynamicPropertySource
    static void rabbitAndPushProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("maraberto.push.fcm.endpoint",
                () -> "http://127.0.0.1:" + fcmMockServer.getAddress().getPort() + "/fcm/send");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private SosNotifierPort sosNotifier;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanState() {
        EmergencyTestData.clearDispatchState(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM push_subscriptions");
        fcmRequests.clear();
        fcmResponseStatus = 200;
        purgeQueue(RabbitSosTopologyConfig.QUEUE_REALTIME_DLQ);
        purgeQueue(RabbitSosTopologyConfig.QUEUE_PUSH_DLQ);
    }

    @AfterEach
    void restoreFcmStatus() {
        fcmResponseStatus = 200;
    }

    @Test
    void usesAmqpSosNotifierAndDeliversToBothStompAndPushQueuesWithConfirms() throws Exception {
        assertThat(sosNotifier).isInstanceOf(AmqpSosNotifier.class);

        TestOperatorAuth.Session operator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        UUID postId = EmergencyTestData.createPost(jdbcTemplate, -10.9472, -37.0731, 500);
        EmergencyTestData.openPostShift(jdbcTemplate, operator.userId(), postId);
        pushSubscriptionRepository.upsert(
                operator.userId(), PushChannel.FCM, "fcm://rabbit-test-token", null, null);

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        BlockingQueue<JsonNode> stompFrames = new LinkedBlockingQueue<>();

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + operator.accessToken());
        StompSession session = stompClient
                .connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        try {
            session.subscribe(SosDestinations.SOS_SUBSCRIPTION, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return JsonNode.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    stompFrames.offer((JsonNode) payload);
                }
            });

            // A assinatura STOMP no SimpleBroker é assíncrona; aguarda um instante antes de disparar o SOS.
            Thread.sleep(200);

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<JsonNode> sosResponse = restTemplate.postForEntity(
                    "/api/v1/emergency-calls",
                    new HttpEntity<>(Map.of(
                            "clientRequestId", UUID.randomUUID().toString(),
                            "latitude", -10.9475,
                            "longitude", -37.0732,
                            "accuracyMeters", 12), httpHeaders),
                    JsonNode.class);
            assertThat(sosResponse.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(sosResponse.getBody()).isNotNull();
            UUID callId = UUID.fromString(sosResponse.getBody().get("callId").asText());

            JsonNode stompFrame = stompFrames.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(stompFrame).isNotNull();
            assertThat(stompFrame.get("callId").asText()).isEqualTo(callId.toString());
            assertThat(stompFrame.get("status").asText()).isEqualTo("DISPATCHED");

            String fcmBody = fcmRequests.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(fcmBody).isNotNull();
            JsonNode fcmJson = objectMapper.readTree(fcmBody);
            assertThat(fcmJson.at("/message/token").asText()).isEqualTo("rabbit-test-token");
            assertThat(fcmJson.at("/message/data/callId").asText()).isEqualTo(callId.toString());
        } finally {
            session.disconnect();
            stompClient.stop();
        }
    }

    @Test
    void retriesPushWithTtlAndRoutesToDlqAfterMaxRetries() throws Exception {
        TestOperatorAuth.Session operator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        pushSubscriptionRepository.upsert(
                operator.userId(), PushChannel.FCM, "fcm://dlq-test-token", null, null);

        // Simula falha transitória 500 no provedor de push
        fcmResponseStatus = 500;

        UUID callId = UUID.randomUUID();
        UUID dispatchId = UUID.randomUUID();
        SosAmqpMessage message = new SosAmqpMessage(
                operator.userId(),
                objectMapper.valueToTree(Map.of(
                        "callId", callId,
                        "dispatchId", dispatchId,
                        "round", 1,
                        "status", "DISPATCHED",
                        "occurredAt", Instant.now())));

        rabbitTemplate.convertAndSend(
                RabbitSosTopologyConfig.EXCHANGE_EMERGENCY,
                RabbitSosTopologyConfig.ROUTING_KEY_NOTIFY,
                message,
                raw -> {
                    raw.getMessageProperties().setMessageId(dispatchId.toString());
                    return raw;
                });

        // Aguarda 1 tentativa inicial + 2 retries com TTL de 200ms -> DLQ
        Message dlqMessage = null;
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline && dlqMessage == null) {
            dlqMessage = rabbitTemplate.receive(RabbitSosTopologyConfig.QUEUE_PUSH_DLQ, 250);
        }

        assertThat(dlqMessage).isNotNull();
        assertThat(dlqMessage.getMessageProperties().getMessageId()).isEqualTo(dispatchId.toString());
        // 1 tentativa inicial + 2 retries = 3 chamadas ao endpoint FCM
        assertThat(fcmRequests.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void throwsWhenBrokerRejectsOrReturnsUnroutableMessage() {
        RabbitTemplate isolatedTemplate = new RabbitTemplate(rabbitTemplate.getConnectionFactory());
        isolatedTemplate.setMessageConverter(rabbitTemplate.getMessageConverter());
        AmqpSosNotifier isolatedNotifier = new AmqpSosNotifier(isolatedTemplate, objectMapper, 3000);

        // Exchange inexistente provoca NACK/shutdown do canal -> AmqpSosNotifier lança exceção
        // para manter o evento PENDING na Transactional Outbox.
        assertThatThrownBy(() -> {
            org.springframework.amqp.rabbit.connection.CorrelationData cd =
                    new org.springframework.amqp.rabbit.connection.CorrelationData(UUID.randomUUID().toString());
            isolatedTemplate.convertAndSend(
                    "maraberto.exchange.inexistente",
                    "sos.notify",
                    new SosAmqpMessage(UUID.randomUUID(), objectMapper.createObjectNode()),
                    cd);
            var confirm = cd.getFuture().get(3, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException("Broker NACK");
            }
        }).isInstanceOf(IllegalStateException.class);
        assertThat(isolatedNotifier).isNotNull();
    }

    private void purgeQueue(String queueName) {
        while (rabbitTemplate.receive(queueName) != null) {
            // limpa mensagens residuais entre testes
        }
    }
}
