package com.maraberto.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.realtime.domain.PushChannel;
import com.maraberto.realtime.domain.PushSubscription;
import com.maraberto.realtime.infra.FcmPushSender;
import com.maraberto.realtime.infra.PushSubscriptionRepository;
import com.maraberto.realtime.infra.SosPushDispatcher;
import com.maraberto.support.PostgisIntegrationTest;
import com.maraberto.support.TestOperatorAuth;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Testes de integração de registro Web Push (VAPID) / FCM e envio para
 * aparelhos com o app fechado.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "maraberto.push.vapid.public-key=BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U",
                "maraberto.push.vapid.private-key=UUxI4O8-FbRouAevSmBQ6o18hgE4nSG3qwvJTfKc-ls",
                "maraberto.push.fcm.enabled=true"
        })
class PushSubscriptionIntegrationTests extends PostgisIntegrationTest {

    private static HttpServer fcmMockServer;
    private static volatile int fcmResponseStatus = 200;
    private static final BlockingQueue<String> fcmRequests = new LinkedBlockingQueue<>();

    static {
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
    static void pushProperties(DynamicPropertyRegistry registry) {
        registry.add("maraberto.push.fcm.endpoint",
                () -> "http://127.0.0.1:" + fcmMockServer.getAddress().getPort() + "/fcm/send");
    }

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
    private SosPushDispatcher pushDispatcher;

    @Autowired
    private FcmPushSender fcmPushSender;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM push_subscriptions");
        fcmRequests.clear();
        fcmResponseStatus = 200;
    }

    @AfterEach
    void resetStatus() {
        fcmResponseStatus = 200;
    }

    @Test
    void returnsConfiguredVapidPublicKeyForAuthenticatedOperator() throws Exception {
        TestOperatorAuth.Session operator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");

        ResponseEntity<String> unauthenticated = restTemplate.getForEntity(
                "/api/v1/push/vapid-public-key", String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(operator.accessToken());
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/v1/push/vapid-public-key",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("enabled").asBoolean()).isTrue();
        assertThat(response.getBody().get("publicKey").asText()).startsWith("BEl62iUYg");
    }

    @Test
    void registersAndUnregistersWebPushAndFcmSubscriptions() throws Exception {
        TestOperatorAuth.Session operator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(operator.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Web Push válido
        ResponseEntity<Void> webPushResponse = restTemplate.postForEntity(
                "/api/v1/push/subscriptions",
                new HttpEntity<>(Map.of(
                        "channel", "WEB_PUSH",
                        "endpoint", "https://push.example.test/sub/123",
                        "p256dh", "p256dh-key",
                        "auth", "auth-secret"), headers),
                Void.class);
        assertThat(webPushResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // FCM válido
        ResponseEntity<Void> fcmResponse = restTemplate.postForEntity(
                "/api/v1/push/subscriptions",
                new HttpEntity<>(Map.of(
                        "channel", "FCM",
                        "token", "fcm-device-token-abc"), headers),
                Void.class);
        assertThat(fcmResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(pushSubscriptionRepository.findByUserId(operator.userId()))
                .hasSize(2)
                .extracting(PushSubscription::channel)
                .containsExactlyInAnyOrder(PushChannel.WEB_PUSH, PushChannel.FCM);

        // Remoção no logout
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/v1/push/subscriptions",
                HttpMethod.DELETE,
                new HttpEntity<>(Map.of("token", "fcm-device-token-abc"), headers),
                Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(pushSubscriptionRepository.findByUserId(operator.userId()))
                .hasSize(1)
                .first()
                .extracting(PushSubscription::channel)
                .isEqualTo(PushChannel.WEB_PUSH);
    }

    @Test
    void rejectsIncompletePushSubscriptionPayloads() throws Exception {
        TestOperatorAuth.Session operator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(operator.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> badWebPush = restTemplate.postForEntity(
                "/api/v1/push/subscriptions",
                new HttpEntity<>(Map.of(
                        "channel", "WEB_PUSH",
                        "endpoint", "https://push.example.test/sub/missing-keys"), headers),
                String.class);
        assertThat(badWebPush.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> badFcm = restTemplate.postForEntity(
                "/api/v1/push/subscriptions",
                new HttpEntity<>(Map.of("channel", "FCM"), headers),
                String.class);
        assertThat(badFcm.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void dispatchesRedactedFcmPushAndRemovesExpiredTokenOn404() throws Exception {
        TestOperatorAuth.Session operator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");

        pushSubscriptionRepository.upsert(
                operator.userId(), PushChannel.FCM, "fcm://device-token-xyz", null, null);

        UUID callId = UUID.randomUUID();
        UUID dispatchId = UUID.randomUUID();
        pushDispatcher.dispatch(operator.userId(), Map.of(
                "callId", callId,
                "dispatchId", dispatchId,
                "round", 1,
                "status", "DISPATCHED",
                "occurredAt", Instant.now()));

        String sentBody = fcmRequests.poll(5, TimeUnit.SECONDS);
        assertThat(sentBody).isNotNull();
        JsonNode json = objectMapper.readTree(sentBody);
        assertThat(json.at("/message/token").asText()).isEqualTo("device-token-xyz");
        assertThat(json.at("/message/android/priority").asText()).isEqualTo("high");
        assertThat(json.at("/message/data/callId").asText()).isEqualTo(callId.toString());
        assertThat(json.at("/message/data/url").asText()).isEqualTo("/operador");

        // Token expirado (404) deve ser removido automaticamente
        fcmResponseStatus = 404;
        fcmPushSender.send(
                pushSubscriptionRepository.findByUserId(operator.userId()).getFirst(),
                objectMapper.valueToTree(Map.of("callId", callId, "status", "DISPATCHED")));
        assertThat(pushSubscriptionRepository.findByUserId(operator.userId())).isEmpty();
    }
}
