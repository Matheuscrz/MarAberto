package com.maraberto.emergency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.support.EmergencyTestData;
import com.maraberto.support.PostgisIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "maraberto.emergency.outbox.sweep-interval=PT1H",
        "maraberto.emergency.outbox.sweep-initial-delay=PT1H"
})
@AutoConfigureMockMvc
class EmergencyCallIntegrationTests extends PostgisIntegrationTest {

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
    void createsCallAndOneDispatchPerEligibleShift() throws Exception {
        double latitude = -10.9472;
        double longitude = -37.0731;
        UUID operatorId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID postId = EmergencyTestData.createPost(jdbcTemplate, latitude, longitude, 500);
        EmergencyTestData.openPostShift(jdbcTemplate, operatorId, postId);

        UUID callId = createCall(latitude, longitude);

        assertThat(count("SELECT count(*) FROM emergency_calls WHERE id = ?", callId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT recipient_user_id FROM emergency_dispatches WHERE emergency_call_id = ?",
                UUID.class, callId))
                .isEqualTo(operatorId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT recipient_post_id FROM emergency_dispatches WHERE emergency_call_id = ?",
                UUID.class, callId))
                .isEqualTo(postId);
        Double distance = jdbcTemplate.queryForObject(
                "SELECT distance_m FROM emergency_dispatches WHERE emergency_call_id = ?",
                Double.class, callId);
        assertThat(distance).isNotNull();
        assertThat(distance.doubleValue()).isLessThan(5.0);
        assertThat(count("SELECT count(*) FROM emergency_outbox_events WHERE aggregate_id = ?", callId))
                .isEqualTo(1);
    }

    @Test
    void retriesWithSameClientRequestIdWithoutDuplicatingCallOrDispatch() throws Exception {
        double latitude = -9.6600;
        double longitude = -35.7200;
        UUID operatorId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID postId = EmergencyTestData.createPost(jdbcTemplate, latitude, longitude, 500);
        EmergencyTestData.openPostShift(jdbcTemplate, operatorId, postId);

        String clientRequestId = UUID.randomUUID().toString();
        String body = body(clientRequestId, latitude, longitude);

        String firstResponse = mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondResponse = mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(secondResponse).get("callId").asText())
                .isEqualTo(objectMapper.readTree(firstResponse).get("callId").asText());
        assertThat(count("SELECT count(*) FROM emergency_calls WHERE client_request_id = ?",
                clientRequestId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM emergency_dispatches d "
                + "JOIN emergency_calls c ON c.id = d.emergency_call_id "
                + "WHERE c.client_request_id = ?", clientRequestId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM emergency_outbox_events e "
                + "JOIN emergency_calls c ON c.id = e.aggregate_id "
                + "WHERE c.client_request_id = ?", clientRequestId)).isEqualTo(1);
    }

    @Test
    void fallsBackToCoordinationWhenNobodyIsOnShift() throws Exception {
        jdbcTemplate.update("DELETE FROM shift_sessions");
        UUID coordinatorId = EmergencyTestData.createOperator(jdbcTemplate, "COORDINATOR");

        UUID callId = createCall(-12.9700, -38.5000);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT recipient_user_id FROM emergency_dispatches WHERE emergency_call_id = ?",
                UUID.class, callId))
                .isEqualTo(coordinatorId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT distance_m FROM emergency_dispatches WHERE emergency_call_id = ?",
                Double.class, callId))
                .isNull();
    }

    @Test
    void resolvesMobileBaseByLastKnownPosition() throws Exception {
        jdbcTemplate.update("DELETE FROM shift_sessions");
        double latitude = -13.0100;
        double longitude = -38.5300;
        UUID operatorId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID baseId = EmergencyTestData.createMobileBase(
                jdbcTemplate, latitude, longitude, 500);
        EmergencyTestData.openMobileShift(jdbcTemplate, operatorId, baseId, latitude, longitude);

        UUID callId = createCall(latitude, longitude);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT recipient_base_id FROM emergency_dispatches WHERE emergency_call_id = ?",
                UUID.class, callId))
                .isEqualTo(baseId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT recipient_user_id FROM emergency_dispatches WHERE emergency_call_id = ?",
                UUID.class, callId))
                .isEqualTo(operatorId);
    }

    @Test
    void rejectsInvalidCoordinates() throws Exception {
        mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientRequestId": "%s",
                                  "latitude": 91,
                                  "longitude": -37
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMaliciousClientRequestIdWithoutEchoingIt() throws Exception {
        mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientRequestId": "<script>alert(1)</script>",
                                  "latitude": -10.9472,
                                  "longitude": -37.0731
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("<script>"));
    }

    @Test
    void storesOutboxPayloadEncryptedAtRest() throws Exception {
        double latitude = -8.0500;
        double longitude = -34.9000;
        UUID operatorId = EmergencyTestData.createOperator(jdbcTemplate, "LIFEGUARD");
        UUID postId = EmergencyTestData.createPost(jdbcTemplate, latitude, longitude, 500);
        EmergencyTestData.openPostShift(jdbcTemplate, operatorId, postId);

        UUID callId = createCall(latitude, longitude);

        String payload = jdbcTemplate.queryForObject(
                "SELECT payload FROM emergency_outbox_events WHERE aggregate_id = ?",
                String.class, callId);

        assertThat(payload).startsWith("enc:v1:");
        assertThat(payload)
                .doesNotContain(String.valueOf(latitude))
                .doesNotContain(String.valueOf(longitude))
                .doesNotContain("clientRequestId");
    }

    @Test
    void returnsRfc9457ProblemForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_PROBLEM_JSON)
                        .content("""
                                {
                                  "clientRequestId": "",
                                  "latitude": 91,
                                  "longitude": -37
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(result -> {
                    JsonNode problem = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(problem.get("type").asText())
                            .isEqualTo("https://maraberto.org/problems/invalid-request");
                    assertThat(problem.get("title").asText()).isEqualTo("Requisição inválida");
                    assertThat(problem.get("status").asInt()).isEqualTo(400);
                    assertThat(problem.get("detail").asText()).isNotBlank();
                    assertThat(problem.get("instance").asText())
                            .isEqualTo("/api/v1/emergency-calls");
                    assertThat(problem.get("errors")).isNotEmpty();
                });
    }

    @Test
    void returnsRfc9457ProblemForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_PROBLEM_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(result -> {
                    JsonNode problem = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(problem.get("type").asText())
                            .isEqualTo("https://maraberto.org/problems/malformed-json");
                    assertThat(problem.get("status").asInt()).isEqualTo(400);
                });
    }

    private UUID createCall(double latitude, double longitude) throws Exception {
        String response = mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), latitude, longitude)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("callId").asText());
    }

    private static String body(String clientRequestId, double latitude, double longitude) {
        return """
                {
                  "clientRequestId": "%s",
                  "latitude": %s,
                  "longitude": %s,
                  "accuracyMeters": 12.5
                }
                """.formatted(clientRequestId, latitude, longitude);
    }

    private Integer count(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, Integer.class, argument);
    }
}
