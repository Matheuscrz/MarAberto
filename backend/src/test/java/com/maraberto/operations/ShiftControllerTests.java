package com.maraberto.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.support.EmergencyTestData;
import com.maraberto.support.PostgisIntegrationTest;
import com.maraberto.support.TestOperatorAuth;
import java.util.Map;
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
 * Ciclo de vida do plantão. É o que torna o operador elegível para o SOS
 * (ADR-018) e alimenta o painel da coordenação.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShiftControllerTests extends PostgisIntegrationTest {

    private static final double LATITUDE = -13.0020;
    private static final double LONGITUDE = -38.5320;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthenticationService authenticationService;

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
    void opensMovesAndClosesTheShift() throws Exception {
        ResponseEntity<String> opened = post("/api/v1/dispatch/shifts",
                Map.of("postId", postId.toString(), "latitude", LATITUDE, "longitude", LONGITUDE));
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode shift = objectMapper.readTree(opened.getBody());
        assertThat(shift.get("postId").asText()).isEqualTo(postId.toString());
        assertThat(shift.get("endedAt").isNull()).isTrue();
        assertThat(shift.get("lastPositionAt").isNull()).isFalse();

        ResponseEntity<String> duplicate = post("/api/v1/dispatch/shifts",
                Map.of("postId", postId.toString()));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(duplicate.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/shift-already-open");

        ResponseEntity<String> current = exchange(
                "/api/v1/dispatch/shifts/current", HttpMethod.GET, null);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(current.getBody()).get("endedAt").isNull()).isTrue();

        ResponseEntity<String> moved = exchange(
                "/api/v1/dispatch/shifts/current/position",
                HttpMethod.PUT,
                Map.of("latitude", LATITUDE + 0.0005, "longitude", LONGITUDE + 0.0005,
                        "accuracyMeters", 8.5));
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(moved.getBody()).get("lastPositionAccuracyMeters").asDouble())
                .isEqualTo(8.5);

        ResponseEntity<String> closed = exchange(
                "/api/v1/dispatch/shifts/current/close", HttpMethod.POST, null);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(closed.getBody()).get("endedAt").isNull()).isFalse();

        ResponseEntity<String> afterClose = exchange(
                "/api/v1/dispatch/shifts/current", HttpMethod.GET, null);
        assertThat(afterClose.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(objectMapper.readTree(afterClose.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/shift-not-found");
    }

    @Test
    void opensShiftOnMobileBase() {
        UUID baseId = EmergencyTestData.createMobileBase(
                jdbcTemplate, LATITUDE, LONGITUDE, 500);

        ResponseEntity<String> opened = post("/api/v1/dispatch/shifts",
                Map.of("mobileBaseId", baseId.toString(),
                        "latitude", LATITUDE, "longitude", LONGITUDE));

        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT mobile_base_id FROM shift_sessions WHERE ended_at IS NULL AND user_id = ?",
                UUID.class, operator.userId()))
                .isEqualTo(baseId);
    }

    @Test
    void rejectsShiftWithoutExactlyOneTarget() throws Exception {
        ResponseEntity<String> response = post("/api/v1/dispatch/shifts",
                Map.of("latitude", LATITUDE, "longitude", LONGITUDE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/invalid-request");
    }

    @Test
    void rejectsShiftOnInactivePost() throws Exception {
        UUID inactivePost = EmergencyTestData.createPost(jdbcTemplate, LATITUDE, LONGITUDE, 500);
        jdbcTemplate.update("UPDATE lifeguard_posts SET active = FALSE WHERE id = ?", inactivePost);

        ResponseEntity<String> response = post("/api/v1/dispatch/shifts",
                Map.of("postId", inactivePost.toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/invalid-request");
    }

    @Test
    void listsActiveTargetsForShift() throws Exception {
        UUID baseId = EmergencyTestData.createMobileBase(
                jdbcTemplate, LATITUDE, LONGITUDE, 900);
        jdbcTemplate.update("""
                UPDATE mobile_bases
                   SET position_updated_at = NOW(), position_accuracy_m = 9.5
                 WHERE id = ?
                """, baseId);
        UUID inactivePost = EmergencyTestData.createPost(jdbcTemplate, LATITUDE, LONGITUDE, 500);
        jdbcTemplate.update("UPDATE lifeguard_posts SET active = FALSE WHERE id = ?", inactivePost);

        ResponseEntity<String> response = exchange(
                "/api/v1/dispatch/shifts/targets", HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode targets = objectMapper.readTree(response.getBody());
        assertThat(targets.isArray()).isTrue();

        JsonNode post = findTarget(targets, postId.toString());
        assertThat(post.get("kind").asText()).isEqualTo("POST");
        assertThat(post.get("beachName").asText()).startsWith("Praia de teste ");
        assertThat(post.get("latitude").asDouble()).isCloseTo(LATITUDE, within(1e-6));
        assertThat(post.get("longitude").asDouble()).isCloseTo(LONGITUDE, within(1e-6));
        assertThat(post.get("coverageRadiusMeters").asInt()).isEqualTo(800);

        JsonNode base = findTarget(targets, baseId.toString());
        assertThat(base.get("kind").asText()).isEqualTo("MOBILE_BASE");
        assertThat(base.get("beachName").isNull()).isTrue();
        assertThat(base.get("positionUpdatedAt").isNull()).isFalse();
        assertThat(base.get("positionAccuracyM").asDouble()).isEqualTo(9.5);
        assertThat(base.get("coverageRadiusMeters").asInt()).isEqualTo(900);

        assertThat(findTargetOrNull(targets, inactivePost.toString()))
                .as("posto inativo não pode receber plantão")
                .isNull();
    }

    @Test
    void rejectsPositionWithOnlyOneCoordinate() throws Exception {
        ResponseEntity<String> response = post("/api/v1/dispatch/shifts",
                Map.of("postId", postId.toString(), "latitude", LATITUDE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/invalid-request");
    }

    @Test
    void rejectsPositionUpdateWithoutCoordinates() throws Exception {
        post("/api/v1/dispatch/shifts",
                Map.of("postId", postId.toString(), "latitude", LATITUDE, "longitude", LONGITUDE));

        ResponseEntity<String> response = exchange(
                "/api/v1/dispatch/shifts/current/position", HttpMethod.PUT, Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/invalid-request");
    }

    private static JsonNode findTarget(JsonNode targets, String id) {
        JsonNode found = findTargetOrNull(targets, id);
        assertThat(found).as("destino %s na lista de plantão", id).isNotNull();
        return found;
    }

    private static JsonNode findTargetOrNull(JsonNode targets, String id) {
        for (JsonNode target : targets) {
            if (id.equals(target.get("id").asText())) {
                return target;
            }
        }
        return null;
    }

    private ResponseEntity<String> post(String path, Map<String, Object> body) {
        return exchange(path, HttpMethod.POST, body);
    }

    private ResponseEntity<String> exchange(
            String path, HttpMethod method, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(operator.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        try {
            String payload = body == null ? null : objectMapper.writeValueAsString(body);
            return restTemplate.exchange(path, method, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Falha ao montar a requisição de teste", exception);
        }
    }
}
