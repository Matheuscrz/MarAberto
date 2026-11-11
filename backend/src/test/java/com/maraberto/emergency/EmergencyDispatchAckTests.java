package com.maraberto.emergency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.support.EmergencyTestData;
import com.maraberto.support.PostgisIntegrationTest;
import com.maraberto.support.TestOperatorAuth;
import java.time.Instant;
import java.util.UUID;
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

/**
 * Ack do SOS por compare-and-set.
 *
 * <p>Dois plantões recebem o mesmo chamado. O primeiro a assumir vence; o outro
 * recebe 409 e o despacho dele é cancelado. É o que impede dois guarda-vidas de
 * saírem para o mesmo chamado sem saber.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "maraberto.emergency.outbox.sweep-initial-delay=PT1H",
                "maraberto.dispatch.max-recipients=3"
        })
class EmergencyDispatchAckTests extends PostgisIntegrationTest {

    private static final double LATITUDE = -12.9740;
    private static final double LONGITUDE = -38.5010;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthenticationService authenticationService;

    private TestOperatorAuth.Session first;
    private TestOperatorAuth.Session second;
    private UUID firstPostId;
    private UUID secondPostId;
    private UUID firstShiftId;

    @BeforeEach
    void setUp() {
        EmergencyTestData.clearDispatchState(jdbcTemplate);

        first = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        second = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        firstPostId = EmergencyTestData.createPost(jdbcTemplate, LATITUDE, LONGITUDE, 800);
        secondPostId = EmergencyTestData.createPost(
                jdbcTemplate, LATITUDE + 0.001, LONGITUDE + 0.001, 800);
        firstShiftId = EmergencyTestData.openPostShift(jdbcTemplate, first.userId(), firstPostId);
        EmergencyTestData.openPostShift(jdbcTemplate, second.userId(), secondPostId);
        // O operador está no ponto do chamado: é da posição do plantão que sai a
        // distância "agora" depois do ack.
        EmergencyTestData.reportPosition(
                jdbcTemplate, firstShiftId, LATITUDE, LONGITUDE, Instant.now());
    }

    @Test
    void firstAckWinsAndCancelsTheOtherDispatch() throws Exception {
        UUID callId = createEmergencyCall();

        // A publicação pós-commit roda em paralelo e pode já ter marcado
        // NOTIFIED; o que importa aqui é que nenhum despacho foi cancelado nem
        // assumido antes do ack.
        assertThat(dispatchStatus(callId, first.userId())).isIn("PENDING", "NOTIFIED");
        assertThat(dispatchStatus(callId, second.userId())).isIn("PENDING", "NOTIFIED");

        assertThat(openCalls(first.accessToken()))
                .anySatisfy(node -> assertThat(node.get("callId").asText())
                        .isEqualTo(callId.toString()));

        ResponseEntity<String> winner = ack(callId, first.accessToken());
        assertThat(winner.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode winnerBody = objectMapper.readTree(winner.getBody());
        assertThat(winnerBody.get("status").asText()).isEqualTo("ACKNOWLEDGED");
        assertThat(winnerBody.get("assignedPostId").asText()).isEqualTo(firstPostId.toString());

        ResponseEntity<String> loser = ack(callId, second.accessToken());
        assertThat(loser.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(loser.getHeaders().getContentType())
                .isNotNull()
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .isTrue());
        JsonNode problem = objectMapper.readTree(loser.getBody());
        assertThat(problem.get("type").asText())
                .isEqualTo("https://maraberto.org/problems/call-already-acknowledged");
        assertThat(problem.get("status").asInt()).isEqualTo(409);

        assertThat(dispatchStatus(callId, first.userId())).isEqualTo("ACKNOWLEDGED");
        assertThat(dispatchStatus(callId, second.userId())).isEqualTo("CANCELLED");
        assertThat(dispatchCancelledAt(callId, second.userId())).isNotNull();
        assertThat(callStatus(callId)).isEqualTo("ACKNOWLEDGED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT assigned_post_id FROM emergency_calls WHERE id = ?",
                UUID.class, callId)).isEqualTo(firstPostId);

        // O chamado sai da fila dos dois consoles.
        assertThat(openCalls(first.accessToken())).isEmpty();
        assertThat(openCalls(second.accessToken())).isEmpty();

        // Mas não some da mesa de quem assumiu: continua como atendimento em
        // curso, com a localização que alimenta a rota até o banhista.
        JsonNode[] assigned = assignedCalls(first.accessToken());
        assertThat(assigned).hasSize(1);
        assertThat(assigned[0].get("callId").asText()).isEqualTo(callId.toString());
        assertThat(assigned[0].get("status").asText()).isEqualTo("ACKNOWLEDGED");
        assertThat(assigned[0].get("latitude").asDouble()).isEqualTo(LATITUDE);
        assertThat(assigned[0].get("longitude").asDouble()).isEqualTo(LONGITUDE);
        assertThat(assigned[0].get("acknowledgedAt").isNull()).isFalse();
        assertThat(assignedCalls(second.accessToken())).isEmpty();

        // Distância atual: da posição do plantão até o banhista, não a da
        // abertura do chamado. Com o operador no ponto, é praticamente zero.
        assertThat(assigned[0].get("currentDistanceMeters").isNull()).isFalse();
        assertThat(assigned[0].get("currentDistanceMeters").asDouble()).isLessThan(1.0);
        assertThat(assigned[0].get("currentPositionAt").isNull()).isFalse();
        assertThat(assigned[0].get("currentPositionStale").asBoolean()).isFalse();

        // Ao se deslocar, a ficha acompanha: ~1 km ao norte do chamado.
        EmergencyTestData.reportPosition(
                jdbcTemplate, firstShiftId, LATITUDE + 0.009, LONGITUDE, Instant.now());
        JsonNode[] moved = assignedCalls(first.accessToken());
        assertThat(moved).hasSize(1);
        assertThat(moved[0].get("currentDistanceMeters").asDouble())
                .as("a distância agora é a do deslocamento, não a da abertura")
                .isBetween(950.0, 1050.0);
    }

    @Test
    void operatorWhoWasNotDispatchedGetsForbiddenWithoutLeakingTheCall() throws Exception {
        UUID callId = createEmergencyCall();

        TestOperatorAuth.Session outsider = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        UUID outsiderPost = EmergencyTestData.createPost(jdbcTemplate, 5.0, 5.0, 800);
        EmergencyTestData.openPostShift(
                jdbcTemplate, outsider.userId(), outsiderPost);

        ResponseEntity<String> response = ack(callId, outsider.accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.get("type").asText())
                .isEqualTo("https://maraberto.org/problems/call-not-assigned");
        assertThat(response.getBody())
                .doesNotContain(String.valueOf(LATITUDE))
                .doesNotContain(String.valueOf(LONGITUDE));
    }

    @Test
    void requiresAuthenticationForDispatchEndpoints() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/dispatch/calls",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .isTrue());
    }

    private ResponseEntity<String> ack(UUID callId, String accessToken) {
        HttpHeaders headers = authorization(accessToken);
        return restTemplate.exchange(
                "/api/v1/dispatch/calls/" + callId + "/ack",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class);
    }

    private JsonNode[] openCalls(String accessToken) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/dispatch/calls",
                HttpMethod.GET,
                new HttpEntity<>(authorization(accessToken)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readValue(response.getBody(), JsonNode[].class);
    }

    private JsonNode[] assignedCalls(String accessToken) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/dispatch/calls/assigned",
                HttpMethod.GET,
                new HttpEntity<>(authorization(accessToken)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readValue(response.getBody(), JsonNode[].class);
    }

    private static HttpHeaders authorization(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private UUID createEmergencyCall() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {
                  "clientRequestId": "%s",
                  "latitude": %s,
                  "longitude": %s,
                  "accuracyMeters": 10.0
                }
                """.formatted(UUID.randomUUID(), LATITUDE, LONGITUDE);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/emergency-calls", new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID callId = UUID.fromString(
                objectMapper.readTree(response.getBody()).get("callId").asText());

        var dispatches = jdbcTemplate.queryForList(
                "SELECT recipient_user_id, recipient_post_id, distance_m "
                        + "FROM emergency_dispatches WHERE emergency_call_id = ?",
                callId);
        assertThat(dispatches).as("despachos do chamado %s", callId).hasSize(2);
        return callId;
    }

    private String dispatchStatus(UUID callId, UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM emergency_dispatches "
                        + "WHERE emergency_call_id = ? AND recipient_user_id = ?",
                String.class, callId, userId);
    }

    private Object dispatchCancelledAt(UUID callId, UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT cancelled_at FROM emergency_dispatches "
                        + "WHERE emergency_call_id = ? AND recipient_user_id = ?",
                Object.class, callId, userId);
    }

    private String callStatus(UUID callId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM emergency_calls WHERE id = ?", String.class, callId);
    }
}
