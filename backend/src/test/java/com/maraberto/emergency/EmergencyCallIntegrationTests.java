package com.maraberto.emergency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.support.PostgisIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EmergencyCallIntegrationTests extends PostgisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsCallDispatchAndPendingOutboxInOneRequest() throws Exception {
        String clientRequestId = UUID.randomUUID().toString();
        String body = """
                {
                  "clientRequestId": "%s",
                  "latitude": -10.9472,
                  "longitude": -37.0731,
                  "accuracyMeters": 12.5
                }
                """.formatted(clientRequestId);

        String response = mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        UUID callId = UUID.fromString(json.get("callId").asText());

        assertThat(json.get("status").asText()).isEqualTo("DISPATCHED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM emergency_calls WHERE id = ?", Integer.class, callId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM emergency_dispatches WHERE emergency_call_id = ?", Integer.class, callId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM emergency_outbox_events WHERE aggregate_id = ? AND status = 'PENDING'",
                Integer.class, callId))
                .isEqualTo(1);
    }

    @Test
    void retriesWithSameClientRequestIdWithoutDuplicatingTheCall() throws Exception {
        String clientRequestId = UUID.randomUUID().toString();
        String body = """
                {
                  "clientRequestId": "%s",
                  "latitude": -10.9472,
                  "longitude": -37.0731
                }
                """.formatted(clientRequestId);

        String firstResponse = mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondResponse = mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(secondResponse).get("callId").asText())
                .isEqualTo(objectMapper.readTree(firstResponse).get("callId").asText());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM emergency_calls WHERE client_request_id = ?",
                Integer.class, clientRequestId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM emergency_outbox_events WHERE aggregate_id = "
                        + "(SELECT id FROM emergency_calls WHERE client_request_id = ?)",
                Integer.class, clientRequestId))
                .isEqualTo(1);
    }

    @Test
    void rejectsInvalidCoordinates() throws Exception {
        mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientRequestId": "invalid-coordinate",
                                  "latitude": 91,
                                  "longitude": -37
                                }
                                """))
                .andExpect(status().isBadRequest());
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
}
