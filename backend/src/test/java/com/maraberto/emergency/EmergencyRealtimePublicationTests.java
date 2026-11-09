package com.maraberto.emergency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.realtime.api.RealtimeDestinations;
import com.maraberto.support.PostgisIntegrationTest;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Prova o caminho completo: {@code POST /api/v1/emergency-calls} → outbox →
 * STOMP → assinante. É o "walking skeleton" do canal operacional.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmergencyRealtimePublicationTests extends PostgisIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deliversRedactedSosNotificationOverStomp() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        BlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
        List<UUID> createdCalls = new ArrayList<>();

        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
                })
                .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        try {
            session.subscribe(RealtimeDestinations.OPERATIONAL_EMERGENCY_CALLS, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return JsonNode.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    frames.add((JsonNode) payload);
                }
            });

            JsonNode notification = awaitNotification(frames, createdCalls);

            assertThat(UUID.fromString(notification.get("callId").asText())).isIn(createdCalls);
            assertThat(notification.get("status").asText()).isEqualTo("DISPATCHED");
            assertThat(notification.has("occurredAt")).isTrue();
            // Redação: nenhum dado pessoal no tópico aberto.
            assertThat(notification.toString())
                    .doesNotContain("latitude")
                    .doesNotContain("longitude")
                    .doesNotContain("clientRequestId")
                    .doesNotContain("-10.9")
                    .doesNotContain("-37.0");
        } finally {
            session.disconnect();
            stompClient.stop();
        }
    }

    /**
     * A assinatura é registrada de forma assíncrona no broker simples. Se o
     * primeiro SOS sair antes do SUBSCRIBE ser processado, ele é perdido
     * (entrega em memória). Novos chamados são enviados até um frame chegar.
     */
    private JsonNode awaitNotification(BlockingQueue<JsonNode> frames, List<UUID> createdCalls) {
        for (int attempt = 0; attempt < 5; attempt++) {
            createdCalls.add(createEmergencyCall());
            try {
                JsonNode frame = frames.poll(2, TimeUnit.SECONDS);
                if (frame != null) {
                    return frame;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Nenhuma notificação STOMP recebida dentro do tempo esperado");
    }

    private UUID createEmergencyCall() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {
                  "clientRequestId": "%s",
                  "latitude": -10.9472,
                  "longitude": -37.0731,
                  "accuracyMeters": 12.5
                }
                """.formatted(UUID.randomUUID());

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/emergency-calls", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        try {
            return UUID.fromString(objectMapper.readTree(response.getBody()).get("callId").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("Resposta do SOS sem callId", exception);
        }
    }
}
