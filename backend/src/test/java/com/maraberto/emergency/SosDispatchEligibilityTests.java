package com.maraberto.emergency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.support.EmergencyTestData;
import com.maraberto.support.PostgisIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Seleção espacial do SOS: <strong>todos</strong> os plantões dentro do raio de
 * cobertura são notificados, não uma amostra dos mais próximos (ADR-018).
 *
 * <p>Os testes olham a tabela {@code emergency_dispatches}: ela é a decisão do
 * PostGIS materializada. O transporte só entrega o que está ali.
 */
@SpringBootTest(properties = {
        "maraberto.emergency.outbox.sweep-interval=PT1H",
        "maraberto.emergency.outbox.sweep-initial-delay=PT1H"
})
@AutoConfigureMockMvc
class SosDispatchEligibilityTests extends PostgisIntegrationTest {

    /** Ponto base isolado dos demais testes (Brasília). */
    private static final double LATITUDE = -15.8000;
    private static final double LONGITUDE = -47.9000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDispatchState() {
        EmergencyTestData.clearDispatchState(jdbcTemplate);
    }

    @Test
    void notifiesEveryShiftInsideTheCoverageRadius() throws Exception {
        double[][] offsets = {
                {0.0000, 0.0000},
                {0.0010, 0.0000},
                {-0.0010, 0.0005},
                {0.0020, -0.0005},
                {-0.0020, -0.0010},
                {0.0025, 0.0015}
        };
        List<UUID> expectedUsers = new ArrayList<>();
        for (double[] offset : offsets) {
            UUID userId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
            UUID postId = EmergencyTestData.createPost(
                    jdbcTemplate, LATITUDE + offset[0], LONGITUDE + offset[1], 500);
            EmergencyTestData.openPostShift(jdbcTemplate, userId, postId);
            expectedUsers.add(userId);
        }

        UUID callId = createCall(LATITUDE, LONGITUDE);

        List<UUID> recipients = jdbcTemplate.queryForList(
                "SELECT recipient_user_id FROM emergency_dispatches "
                        + "WHERE emergency_call_id = ? ORDER BY distance_m",
                UUID.class, callId);

        // O requisito é notificar todos os que estão no raio — seis plantões,
        // não os três mais próximos de um cap antigo.
        assertThat(recipients).hasSize(offsets.length);
        assertThat(recipients).containsExactlyInAnyOrderElementsOf(expectedUsers);
        assertThat(count("SELECT count(*) FROM emergency_outbox_events WHERE aggregate_id = ?", callId))
                .as("um evento de outbox por destinatário")
                .isEqualTo(offsets.length);
    }

    @Test
    void excludesShiftOutsideItsCoverageRadiusWhenSomeoneIsCloser() throws Exception {
        UUID nearUser = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID nearPost = EmergencyTestData.createPost(
                jdbcTemplate, LATITUDE + 0.0010, LONGITUDE, 500);
        EmergencyTestData.openPostShift(jdbcTemplate, nearUser, nearPost);

        // ~2,2 km do chamado: fora do raio do posto (300 m).
        UUID farUser = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID farPost = EmergencyTestData.createPost(
                jdbcTemplate, LATITUDE + 0.0200, LONGITUDE, 300);
        EmergencyTestData.openPostShift(jdbcTemplate, farUser, farPost);

        UUID callId = createCall(LATITUDE, LONGITUDE);

        assertThat(recipientsOf(callId)).containsExactly(nearUser);
        assertThat(count("SELECT count(*) FROM emergency_dispatches WHERE emergency_call_id = ?", callId))
                .isEqualTo(1);
    }

    @Test
    void fallsBackToNearestShiftsWhenNobodyIsInsideTheRadius() throws Exception {
        // Raio de 100 m e postos a partir de 1 km: ninguém é elegível pelo raio,
        // então o chamado usa o KNN puro. O fallback é limitado (3) de propósito:
        // é degradação, não a seleção normal.
        UUID nearest = createPostShift(0.0090);
        UUID second = createPostShift(0.0135);
        UUID third = createPostShift(0.0180);
        UUID fourth = createPostShift(0.0225);
        UUID fifth = createPostShift(0.0270);

        UUID callId = createCall(LATITUDE, LONGITUDE);

        assertThat(recipientsOf(callId)).containsExactly(nearest, second, third);
        assertThat(recipientsOf(callId))
                .as("os vizinhos mais distantes ficam fora do fallback limitado")
                .doesNotContain(fourth, fifth);
    }

    @Test
    void marksMobileBasePositionAsStaleWhenItWasNotReportedRecently() throws Exception {
        UUID userId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID baseId = EmergencyTestData.createMobileBase(
                jdbcTemplate, LATITUDE, LONGITUDE, 500);
        UUID shiftId = EmergencyTestData.openMobileShift(
                jdbcTemplate, userId, baseId, null, null);

        UUID withoutPosition = createCall(LATITUDE, LONGITUDE);
        assertThat(recipientBaseOf(withoutPosition)).isEqualTo(baseId);
        assertThat(positionStaleOf(withoutPosition)).isTrue();

        jdbcTemplate.update("""
                UPDATE shift_sessions
                SET last_position = CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography),
                    last_position_at = NOW() - INTERVAL '2 hours'
                WHERE id = ?
                """, LONGITUDE, LATITUDE, shiftId);

        UUID oldPosition = createCall(LATITUDE, LONGITUDE);
        assertThat(positionStaleOf(oldPosition)).isTrue();

        jdbcTemplate.update(
                "UPDATE shift_sessions SET last_position_at = NOW() WHERE id = ?", shiftId);

        UUID freshPosition = createCall(LATITUDE, LONGITUDE);
        assertThat(positionStaleOf(freshPosition)).isFalse();
    }

    @Test
    void notifiesPostShiftByTheReportedPositionInsteadOfTheStructure() throws Exception {
        // Posto a ~220 km do chamado (o cadastro é da estrutura, não de onde a
        // pessoa está). O plantão reportou posição no ponto do chamado: a
        // distância despachada tem de refletir o operador, não o posto.
        UUID userId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID postId = EmergencyTestData.createPost(
                jdbcTemplate, LATITUDE + 2.0, LONGITUDE + 2.0, 500);
        UUID shiftId = EmergencyTestData.openPostShift(jdbcTemplate, userId, postId);
        EmergencyTestData.reportPosition(
                jdbcTemplate, shiftId, LATITUDE, LONGITUDE, Instant.now());

        UUID callId = createCall(LATITUDE, LONGITUDE);

        assertThat(recipientsOf(callId)).containsExactly(userId);
        assertThat(distanceOf(callId, userId))
                .as("a distância é a do plantão, não a do posto cadastrado")
                .isLessThan(50.0);
        assertThat(positionStaleOf(callId)).isFalse();
    }

    @Test
    void excludesPostShiftWhoseReportedPositionIsOutsideTheRadius() throws Exception {
        // Controle: plantão no ponto do chamado, garante que o raio foi usado e
        // que o fallback do KNN não entra em cena.
        UUID control = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID controlPost = EmergencyTestData.createPost(jdbcTemplate, LATITUDE, LONGITUDE, 500);
        EmergencyTestData.openPostShift(jdbcTemplate, control, controlPost);

        // Posto dentro do raio, mas o operador está a ~2,2 km: fora do raio.
        UUID away = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID awayPost = EmergencyTestData.createPost(
                jdbcTemplate, LATITUDE + 0.0010, LONGITUDE, 500);
        UUID awayShift = EmergencyTestData.openPostShift(jdbcTemplate, away, awayPost);
        EmergencyTestData.reportPosition(
                jdbcTemplate, awayShift, LATITUDE + 0.0200, LONGITUDE, Instant.now());

        UUID callId = createCall(LATITUDE, LONGITUDE);

        assertThat(recipientsOf(callId)).containsExactly(control);
    }

    @Test
    void marksPostShiftPositionAsStaleWithoutReportedPositionOrWhenItIsOld() throws Exception {
        UUID userId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID postId = EmergencyTestData.createPost(jdbcTemplate, LATITUDE, LONGITUDE, 500);
        UUID shiftId = EmergencyTestData.openPostShift(jdbcTemplate, userId, postId);

        UUID withoutPosition = createCall(LATITUDE, LONGITUDE);
        assertThat(recipientsOf(withoutPosition)).containsExactly(userId);
        assertThat(positionStaleOf(withoutPosition))
                .as("sem posição reportada a distância é a do cadastro do posto, não a do operador")
                .isTrue();

        EmergencyTestData.reportPosition(
                jdbcTemplate, shiftId, LATITUDE, LONGITUDE, Instant.now().minus(2, ChronoUnit.HOURS));

        UUID oldPosition = createCall(LATITUDE, LONGITUDE);
        assertThat(positionStaleOf(oldPosition)).isTrue();

        EmergencyTestData.reportPosition(
                jdbcTemplate, shiftId, LATITUDE, LONGITUDE, Instant.now());

        UUID freshPosition = createCall(LATITUDE, LONGITUDE);
        assertThat(positionStaleOf(freshPosition)).isFalse();
    }

    /** Posto a {@code latitudeOffset} graus ao norte, com raio de 100 m. */
    private UUID createPostShift(double latitudeOffset) {
        UUID userId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID postId = EmergencyTestData.createPost(
                jdbcTemplate, LATITUDE + latitudeOffset, LONGITUDE, 100);
        EmergencyTestData.openPostShift(jdbcTemplate, userId, postId);
        return userId;
    }

    private UUID createCall(double latitude, double longitude) throws Exception {
        String response = mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientRequestId": "%s",
                                  "latitude": %s,
                                  "longitude": %s,
                                  "accuracyMeters": 8.0
                                }
                                """.formatted(UUID.randomUUID(), latitude, longitude)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return UUID.fromString(body.get("callId").asText());
    }

    private List<UUID> recipientsOf(UUID callId) {
        return jdbcTemplate.queryForList(
                "SELECT recipient_user_id FROM emergency_dispatches "
                        + "WHERE emergency_call_id = ? ORDER BY distance_m",
                UUID.class, callId);
    }

    private UUID recipientBaseOf(UUID callId) {
        return jdbcTemplate.queryForObject(
                "SELECT recipient_base_id FROM emergency_dispatches WHERE emergency_call_id = ?",
                UUID.class, callId);
    }

    private double distanceOf(UUID callId, UUID userId) {
        BigDecimal meters = jdbcTemplate.queryForObject(
                "SELECT distance_m FROM emergency_dispatches "
                        + "WHERE emergency_call_id = ? AND recipient_user_id = ?",
                BigDecimal.class, callId, userId);
        return meters == null ? Double.NaN : meters.doubleValue();
    }

    private boolean positionStaleOf(UUID callId) {
        Boolean stale = jdbcTemplate.queryForObject(
                "SELECT position_stale FROM emergency_dispatches WHERE emergency_call_id = ?",
                Boolean.class, callId);
        return Boolean.TRUE.equals(stale);
    }

    private Integer count(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, Integer.class, argument);
    }
}
