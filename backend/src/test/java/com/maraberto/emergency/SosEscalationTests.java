package com.maraberto.emergency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.emergency.application.SosEscalationService;
import com.maraberto.support.EmergencyTestData;
import com.maraberto.support.PostgisIntegrationTest;
import java.util.List;
import java.util.Map;
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
 * Escalonamento do SOS: quando ninguém assume, o chamado é reaberto para anéis
 * cada vez maiores (5, 7, 10, 12, 15 e 20 km) e, esgotada a escada, vai para a
 * coordenação (ADR-018).
 *
 * <p>O job é chamado explicitamente: o sweep periódico fica desligado nos testes
 * (o PostGIS é compartilhado por toda a JVM, e um anel aberto por conta própria
 * mudaria o destinatário esperado de outra classe).
 */
@SpringBootTest(properties = {
        "maraberto.emergency.outbox.sweep-interval=PT1H",
        "maraberto.emergency.outbox.sweep-initial-delay=PT1H",
        "maraberto.dispatch.escalation.sweep-interval=PT1H",
        "maraberto.dispatch.escalation.sweep-initial-delay=PT1H"
})
@AutoConfigureMockMvc
class SosEscalationTests extends PostgisIntegrationTest {

    /** Ponto base isolado dos demais testes (oeste da Bahia). */
    private static final double LATITUDE = -12.4000;
    private static final double LONGITUDE = -45.2000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SosEscalationService escalationService;

    @BeforeEach
    void cleanDispatchState() {
        EmergencyTestData.clearDispatchState(jdbcTemplate);
    }

    @Test
    void opensTheFirstRingForTheShiftThatWasNotNotifiedYet() throws Exception {
        // Raio do posto de 500 m e três plantões próximos: o round 1 cai no KNN
        // de fallback (3 destinatários). O quarto, a ~3 km, só aparece quando a
        // escada abre o anel de 5 km.
        shiftAt(0.0090);
        shiftAt(0.0120);
        shiftAt(0.0140);
        UUID ringUser = shiftAt(0.0270);

        UUID callId = createCall(LATITUDE, LONGITUDE);
        assertThat(roundsOf(callId, ringUser))
                .as("o round 1 não alcança quem está fora do raio e do KNN")
                .isEmpty();

        forceEscalationDue(callId);
        escalationService.escalateDueCalls();

        assertThat(roundsOf(callId, ringUser)).containsExactly(2);
        assertThat(escalationState(callId))
                .containsEntry("step", 1)
                .containsEntry("finished", false);
    }

    @Test
    void walksPastEmptyRingsUntilItFindsSomeone() throws Exception {
        // Plantões próximos consomem o round 1; o de ~9 km está fora dos anéis
        // de 5 e 7 km, então a escada precisa pular direto para o de 10 km.
        shiftAt(0.0090);
        shiftAt(0.0120);
        shiftAt(0.0140);
        UUID farUser = shiftAt(0.0810);

        UUID callId = createCall(LATITUDE, LONGITUDE);
        assertThat(roundsOf(callId, farUser)).isEmpty();

        forceEscalationDue(callId);
        escalationService.escalateDueCalls();

        assertThat(roundsOf(callId, farUser))
                .as("rodada 4 = anel de 10 km (2 anéis pulados na mesma passada)")
                .containsExactly(4);
        assertThat(escalationState(callId)).containsEntry("step", 3);
    }

    @Test
    void handsTheCallToCoordinationWhenTheLadderEnds() throws Exception {
        UUID coordinator = EmergencyTestData.createOperator(jdbcTemplate, "COORDINATOR");
        // ~25 km: fora do teto do round 1 (15 km) e de todos os anéis.
        UUID unreachable = shiftAt(0.2250);

        UUID callId = createCall(LATITUDE, LONGITUDE);
        assertThat(roundsOf(callId, unreachable)).isEmpty();
        assertThat(roundsOf(callId, coordinator))
                .as("o round 1 já aciona a coordenação quando ninguém alcança o chamado")
                .containsExactly(1);

        forceEscalationDue(callId);
        escalationService.escalateDueCalls();

        assertThat(roundsOf(callId, unreachable)).isEmpty();
        assertThat(roundsOf(callId, coordinator))
                .as("coordenação já avisada no round 1 não recebe o alerta de novo")
                .containsExactly(1);
        assertThat(escalationState(callId))
                .as("escada esgotada encerra o escalonamento do chamado")
                .containsEntry("step", 6)
                .containsEntry("finished", true);
    }

    @Test
    void doesNotEscalateACallThatWasAlreadyAcknowledged() throws Exception {
        shiftAt(0.0090);
        shiftAt(0.0120);
        shiftAt(0.0140);
        UUID ringUser = shiftAt(0.0270);

        UUID callId = createCall(LATITUDE, LONGITUDE);
        jdbcTemplate.update("UPDATE emergency_calls SET status = 'ACKNOWLEDGED' WHERE id = ?", callId);

        forceEscalationDue(callId);
        escalationService.escalateDueCalls();

        assertThat(roundsOf(callId, ringUser))
                .as("chamado assumido não acorda mais ninguém")
                .isEmpty();
    }

    @Test
    void ignoresShiftsThatStoppedSendingSignal() throws Exception {
        shiftAt(0.0090);
        shiftAt(0.0120);
        shiftAt(0.0140);

        UUID userId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID postId = EmergencyTestData.createPost(
                jdbcTemplate, LATITUDE + 0.0270, LONGITUDE, 500);
        UUID shiftId = EmergencyTestData.openPostShift(jdbcTemplate, userId, postId);
        // Plantão fantasma: o console parou de mandar sinal (> lease de 15 min).
        jdbcTemplate.update(
                "UPDATE shift_sessions SET last_seen_at = NOW() - INTERVAL '1 hour' WHERE id = ?",
                shiftId);

        UUID callId = createCall(LATITUDE, LONGITUDE);
        forceEscalationDue(callId);
        escalationService.escalateDueCalls();

        assertThat(roundsOf(callId, userId))
                .as("o anel respeita o lease: quem não está com o console aberto não é chamado")
                .isEmpty();
    }

    // --- apoio -----------------------------------------------------------

    private UUID shiftAt(double latitudeOffset) {
        UUID userId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID postId = EmergencyTestData.createPost(
                jdbcTemplate, LATITUDE + latitudeOffset, LONGITUDE, 500);
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

    /** Antecipa o prazo do próximo anel, como se o tempo tivesse passado. */
    private void forceEscalationDue(UUID callId) {
        jdbcTemplate.update(
                "UPDATE emergency_calls SET next_escalation_at = NOW() - INTERVAL '1 second' "
                        + "WHERE id = ?",
                callId);
    }

    private List<Integer> roundsOf(UUID callId, UUID userId) {
        return jdbcTemplate.queryForList(
                "SELECT round FROM emergency_dispatches "
                        + "WHERE emergency_call_id = ? AND recipient_user_id = ? ORDER BY round",
                Integer.class,
                callId,
                userId);
    }

    private Map<String, Object> escalationState(UUID callId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT escalation_step, next_escalation_at FROM emergency_calls WHERE id = ?",
                callId);
        return Map.of(
                "step", ((Number) row.get("escalation_step")).intValue(),
                "finished", row.get("next_escalation_at") == null);
    }
}
