package com.maraberto.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.realtime.api.SosNotifierPort;
import com.maraberto.realtime.infra.AmqpSosNotifier;
import com.maraberto.support.AbstractPostgisTest;
import com.maraberto.support.EmergencyTestData;
import com.maraberto.support.TestOperatorAuth;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Prova a degradação segura quando o RabbitMQ está indisponível com o modo
 * padrão ativo ({@code maraberto.realtime.rabbit.enabled=true}):
 *
 * <ul>
 *   <li>o boot completa e {@code /actuator/health} permanece {@code 200 UP};</li>
 *   <li>o {@code POST /api/v1/emergency-calls} responde {@code 201} sem bloquear
 *       no broker e persiste o evento {@code PENDING} na Transactional Outbox;</li>
 *   <li>o ack do SOS continua funcionando mesmo sem o broker para cancelamento.</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "maraberto.emergency.outbox.async-dispatch=true",
                "maraberto.emergency.outbox.sweep-interval=PT1H",
                "maraberto.emergency.outbox.sweep-initial-delay=PT1H",
                "spring.rabbitmq.connection-timeout=300ms",
                "maraberto.realtime.rabbit.confirm-timeout-ms=500"
        })
class RabbitOutboxFallbackIntegrationTests extends AbstractPostgisTest {

    @DynamicPropertySource
    static void unreachableBroker(DynamicPropertyRegistry registry) {
        // Porta local fechada: simula RabbitMQ fora do ar desde o boot.
        registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
        registry.add("spring.rabbitmq.port", () -> 1);
    }

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

    @Test
    void keepsHealthUpAndFallsBackToDatabaseOutboxWhenRabbitIsOffline() throws Exception {
        assertThat(sosNotifier).isInstanceOf(AmqpSosNotifier.class);

        ResponseEntity<JsonNode> health = restTemplate.getForEntity("/actuator/health", JsonNode.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).isNotNull();
        assertThat(health.getBody().path("status").asText()).isEqualTo("UP");

        TestOperatorAuth.Session operator1 = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        TestOperatorAuth.Session operator2 = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        UUID post1Id = EmergencyTestData.createPost(jdbcTemplate, -10.9472, -37.0731, 500);
        UUID post2Id = EmergencyTestData.createPost(jdbcTemplate, -10.9473, -37.0732, 500);
        EmergencyTestData.openPostShift(jdbcTemplate, operator1.userId(), post1Id);
        EmergencyTestData.openPostShift(jdbcTemplate, operator2.userId(), post2Id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Instant startedAt = Instant.now();
        ResponseEntity<JsonNode> sosResponse = restTemplate.postForEntity(
                "/api/v1/emergency-calls",
                new HttpEntity<>(Map.of(
                        "clientRequestId", UUID.randomUUID().toString(),
                        "latitude", -10.9472,
                        "longitude", -37.0731,
                        "accuracyMeters", 10), headers),
                JsonNode.class);
        Duration elapsed = Duration.between(startedAt, Instant.now());

        assertThat(sosResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sosResponse.getBody()).isNotNull();
        // Publicação assíncrona em Virtual Thread não deve travar o HTTP do SOS.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));

        UUID callId = UUID.fromString(sosResponse.getBody().get("callId").asText());

        // Aguarda a tentativa assíncrona registrar a falha e manter o evento PENDING no banco.
        long deadline = System.currentTimeMillis() + 3000;
        Integer attempts = 0;
        while (System.currentTimeMillis() < deadline && (attempts == null || attempts == 0)) {
            attempts = jdbcTemplate.queryForObject(
                    "SELECT MAX(attempts) FROM emergency_outbox_events WHERE aggregate_id = ?",
                    Integer.class, callId);
            if (attempts == null || attempts == 0) {
                Thread.sleep(50);
            }
        }

        assertThat(attempts).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM emergency_outbox_events WHERE aggregate_id = ?",
                String.class, callId))
                .containsOnly("PENDING");
        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM emergency_dispatches WHERE emergency_call_id = ?",
                String.class, callId))
                .containsOnly("PENDING");

        // Mesmo com RabbitMQ fora, o operador consegue assumir (ack) o chamado pelo banco.
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(operator1.accessToken());
        ResponseEntity<String> ackResponse = restTemplate.postForEntity(
                "/api/v1/dispatch/calls/" + callId + "/ack",
                new HttpEntity<>(authHeaders),
                String.class);
        assertThat(ackResponse.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
