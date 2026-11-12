package com.maraberto.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.operations.application.ShiftService;
import com.maraberto.support.EmergencyTestData;
import com.maraberto.support.PostgisIntegrationTest;
import com.maraberto.support.TestOperatorAuth;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Encerramento do plantão por inatividade (lease/heartbeat).
 *
 * <p>Quando o guarda-vidas fecha o app à força, desliga o aparelho ou perde a
 * rede, o cliente não consegue avisar o servidor. O plantão vale por um
 * <em>lease</em>: o console renova o sinal de vida enquanto está aberto e o
 * turno expira sozinho quando o sinal para. Estes testes cobrem as três defesas
 * — expiração preguiçosa na leitura, exclusão da elegibilidade do SOS e o job
 * de carimbo — sem depender de tempo real de execução.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ShiftIdleExpiryTests extends PostgisIntegrationTest {

    private static final double LATITUDE = -16.6800;
    private static final double LONGITUDE = -49.2500;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private ShiftService shiftService;

    @Autowired
    private MockMvc mockMvc;

    private TestOperatorAuth.Session operator;
    private UUID postId;

    @BeforeEach
    void setUp() {
        EmergencyTestData.clearDispatchState(jdbcTemplate);
        operator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        postId = EmergencyTestData.createPost(jdbcTemplate, LATITUDE, LONGITUDE, 800);
    }

    @Test
    void closesShiftWhenTheConsoleStopsSendingSignal() throws Exception {
        UUID shiftId = openShift();
        ageSignal(shiftId, 20);

        ResponseEntity<String> current = exchange(
                "/api/v1/dispatch/shifts/current", HttpMethod.GET, null);

        assertThat(current.getStatusCode())
                .as("plantão sem sinal de vida não pode continuar aberto")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(objectMapper.readTree(current.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/shift-not-found");
        assertThat(endReasonOf(shiftId)).isEqualTo("IDLE_TIMEOUT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ended_at FROM shift_sessions WHERE id = ?", Instant.class, shiftId))
                .as("o fim do turno é o último sinal, não o momento da leitura")
                .isNotNull();
    }

    @Test
    void heartbeatRenewsTheLease() throws Exception {
        UUID shiftId = openShift();
        // Ainda dentro do prazo, mas perto do limite: o batimento precisa valer.
        ageSignal(shiftId, 14);

        ResponseEntity<String> heartbeat = exchange(
                "/api/v1/dispatch/shifts/current/heartbeat", HttpMethod.POST, null);

        assertThat(heartbeat.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_seen_at FROM shift_sessions WHERE id = ?", Instant.class, shiftId))
                .isAfter(Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThat(exchange("/api/v1/dispatch/shifts/current", HttpMethod.GET, null).getStatusCode())
                .as("batimento mantém o plantão aberto")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void heartbeatWithoutShiftIsNotFound() {
        ResponseEntity<String> response = exchange(
                "/api/v1/dispatch/shifts/current/heartbeat", HttpMethod.POST, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cannotMoveAnExpiredShift() {
        UUID shiftId = openShift();
        ageSignal(shiftId, 20);

        ResponseEntity<String> moved = exchange(
                "/api/v1/dispatch/shifts/current/position",
                HttpMethod.PUT,
                Map.of("latitude", LATITUDE + 0.001, "longitude", LONGITUDE + 0.001));

        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // O 404 não depende do carimbo: a linha só é anotada no próximo sweep.
        shiftService.closeIdleShifts();
        assertThat(endReasonOf(shiftId)).isEqualTo("IDLE_TIMEOUT");
    }

    @Test
    void closingAnExpiredShiftReportsIdleTimeout() throws Exception {
        UUID shiftId = openShift();
        ageSignal(shiftId, 20);

        ResponseEntity<String> closed = exchange(
                "/api/v1/dispatch/shifts/current/close", HttpMethod.POST, null);

        assertThat(closed.getStatusCode())
                .as("encerrar um plantão já vencido é idempotente, não erro")
                .isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(closed.getBody());
        assertThat(body.get("endedAt").isNull()).isFalse();
        assertThat(body.get("endedReason").asText()).isEqualTo("IDLE_TIMEOUT");
    }

    @Test
    void expiredShiftNoLongerReceivesTheSos() throws Exception {
        UUID shiftId = openShift();
        ageSignal(shiftId, 20);

        UUID callId = createCall();

        List<UUID> recipients = jdbcTemplate.queryForList(
                "SELECT recipient_user_id FROM emergency_dispatches WHERE emergency_call_id = ?",
                UUID.class, callId);
        assertThat(recipients)
                .as("app fechado à força não pode receber chamado")
                .doesNotContain(operator.userId());
    }

    @Test
    void freshShiftStillReceivesTheSos() throws Exception {
        openShift();

        UUID callId = createCall();

        List<UUID> recipients = jdbcTemplate.queryForList(
                "SELECT recipient_user_id FROM emergency_dispatches WHERE emergency_call_id = ?",
                UUID.class, callId);
        assertThat(recipients).contains(operator.userId());
    }

    @Test
    void sweepStampsOnlyTheExpiredShifts() {
        UUID expired = openShift();
        ageSignal(expired, 20);

        UUID freshId = UUID.randomUUID();
        UUID otherUser = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        jdbcTemplate.update("""
                INSERT INTO shift_sessions (id, user_id, post_id, started_at, last_seen_at)
                VALUES (?, ?, ?, NOW(), NOW())
                """, freshId, otherUser, postId);

        int closed = shiftService.closeIdleShifts();

        assertThat(closed).isEqualTo(1);
        assertThat(endReasonOf(expired)).isEqualTo("IDLE_TIMEOUT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ended_at FROM shift_sessions WHERE id = ?", Instant.class, freshId))
                .as("plantão com sinal de vida continua aberto")
                .isNull();
    }

    private UUID openShift() {
        ResponseEntity<String> opened = exchange("/api/v1/dispatch/shifts",
                HttpMethod.POST,
                Map.of("postId", postId.toString(), "latitude", LATITUDE, "longitude", LONGITUDE));
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(readJson(opened).get("id").asText());
    }

    /** Move o último sinal de vida para o passado, simulando o app fechado. */
    private void ageSignal(UUID shiftId, int minutes) {
        jdbcTemplate.update("""
                UPDATE shift_sessions
                SET last_seen_at = NOW() - make_interval(mins => ?)
                WHERE id = ?
                """, minutes, shiftId);
    }

    private String endReasonOf(UUID shiftId) {
        return jdbcTemplate.queryForObject(
                "SELECT ended_reason FROM shift_sessions WHERE id = ?", String.class, shiftId);
    }

    private UUID createCall() throws Exception {
        String response = mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientRequestId": "%s",
                                  "latitude": %s,
                                  "longitude": %s,
                                  "accuracyMeters": 8.0
                                }
                                """.formatted(UUID.randomUUID(), LATITUDE, LONGITUDE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("callId").asText());
    }

    private JsonNode readJson(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    private ResponseEntity<String> exchange(
            String path, HttpMethod method, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(operator.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        try {
            String payload = body == null ? null : objectMapper.writeValueAsString(body);
            return restTemplate.exchange(
                    path, method, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }
}
