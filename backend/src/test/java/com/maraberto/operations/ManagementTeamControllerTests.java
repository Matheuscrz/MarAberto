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
 * Painel da coordenação (RF40/RN17): equipe em plantão, última posição e volume
 * do dia.
 *
 * <p>A lista é a mesma fonte do mapa de situação e da aba de equipe: só
 * plantões abertos com lease vivo, com **um** ponto por turno. Encerrar o
 * plantão ou deixar o lease vencer remove a linha na leitura seguinte; a
 * resposta nunca é cacheada e outra função não vê a coordenação.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ManagementTeamControllerTests extends PostgisIntegrationTest {

    private static final double LATITUDE = -12.9020;
    private static final double LONGITUDE = -38.4320;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthenticationService authenticationService;

    private TestOperatorAuth.Session coordinator;
    private TestOperatorAuth.Session lifeguard;
    private UUID postId;
    private UUID beachId;

    @BeforeEach
    void setUp() {
        coordinator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "COORDINATOR");
        lifeguard = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        postId = EmergencyTestData.createPost(jdbcTemplate, LATITUDE, LONGITUDE, 900);
        beachId = jdbcTemplate.queryForObject(
                "SELECT beach_id FROM lifeguard_posts WHERE id = ?", UUID.class, postId);
    }

    @Test
    void listsOpenShiftWithPositionAndDailyCounters() throws Exception {
        openShiftWithPosition(lifeguard.accessToken(), LATITUDE + 0.0004, LONGITUDE + 0.0006);

        seedOccurrences();
        seedFlagUpdate();

        ResponseEntity<String> response = team(coordinator.accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // RN17: coordenada de operador não pode ser cacheada em lugar nenhum.
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst(HttpHeaders.PRAGMA)).isEqualTo("no-cache");

        JsonNode member = onlyMember(response);
        assertThat(member.get("userId").asText()).isEqualTo(lifeguard.userId().toString());
        assertThat(member.get("shiftId").asText()).isNotBlank();
        // O nome é decifrado no `identity`, não no `operations`.
        assertThat(member.get("name").asText()).startsWith("Operador ");
        assertThat(member.get("badgeNumber").asText()).startsWith("T-");
        assertThat(member.get("stationName").asText()).startsWith("Posto de teste");
        assertThat(member.get("beachName").asText()).startsWith("Praia de teste");
        assertThat(member.get("leaseAlive").asBoolean()).isTrue();
        assertThat(member.get("onShiftSince").isNull()).isFalse();
        assertThat(member.get("lastSeenAt").isNull()).isFalse();

        JsonNode position = member.get("lastPosition");
        assertThat(position.get("latitude").asDouble()).isCloseTo(LATITUDE + 0.0004, within(1e-6));
        assertThat(position.get("longitude").asDouble()).isCloseTo(LONGITUDE + 0.0006, within(1e-6));
        assertThat(position.get("accuracyMeters").asDouble()).isEqualTo(9.5);
        assertThat(position.get("at").isNull()).isFalse();
        assertThat(position.get("stale").asBoolean()).isFalse();

        // Ocorrências do dia = 3 (2 de hoje + 1 de ontem fora do recorte).
        assertThat(member.get("occurrencesToday").asInt()).isEqualTo(3);
        // Atendimento em campo é a origem FIELD_ATTENDED (só uma).
        assertThat(member.get("fieldAttendedToday").asInt()).isEqualTo(1);
        // Sinalizações lançadas pelo operador no dia (o lançamento alheio não conta).
        assertThat(member.get("signalsToday").asInt()).isEqualTo(1);
        // CIOSP pendente: ficha em atendimento com transcrição pendente.
        assertThat(member.get("ciospPending").asInt()).isEqualTo(1);
    }

    @Test
    void marksPositionOlderThanTheStaleWindow() throws Exception {
        openShiftWithPosition(lifeguard.accessToken(), LATITUDE, LONGITUDE);

        // O console continua batendo o lease, mas a última posição é velha: a
        // coordenação vê "sem sinal", nunca um ponto que parece atual.
        jdbcTemplate.update("""
                UPDATE shift_sessions
                   SET last_position_at = NOW() - INTERVAL '40 minutes',
                       last_seen_at = NOW()
                 WHERE user_id = ?
                """, lifeguard.userId());

        JsonNode position = onlyMember(team(coordinator.accessToken())).get("lastPosition");
        assertThat(position.get("stale").asBoolean()).isTrue();
        assertThat(position.get("latitude").asDouble()).isCloseTo(LATITUDE, within(1e-6));
    }

    @Test
    void closingTheShiftRemovesTheGuardFromTheList() throws Exception {
        openShiftWithPosition(lifeguard.accessToken(), LATITUDE, LONGITUDE);
        assertThat(team(coordinator.accessToken()).getBody()).contains(lifeguard.userId().toString());

        ResponseEntity<String> closed = exchange(
                "/api/v1/dispatch/shifts/current/close",
                HttpMethod.POST,
                null,
                lifeguard.accessToken());
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode members = members(team(coordinator.accessToken()));
        assertThat(members).isEmpty();
    }

    @Test
    void hidesShiftWhoseLeaseExpired() throws Exception {
        openShiftWithPosition(lifeguard.accessToken(), LATITUDE, LONGITUDE);

        // Sem sinal de vida, o plantão não está mais "em campo" para a
        // coordenação — mesmo antes do job de expiração marcar o encerramento.
        jdbcTemplate.update("""
                UPDATE shift_sessions
                   SET last_seen_at = NOW() - INTERVAL '20 minutes'
                 WHERE user_id = ?
                """, lifeguard.userId());

        assertThat(members(team(coordinator.accessToken()))).isEmpty();
    }

    @Test
    void lifeguardCannotReadTheTeam() {
        openShiftWithPosition(lifeguard.accessToken(), LATITUDE, LONGITUDE);

        ResponseEntity<String> response = team(lifeguard.accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * Ocorrências do dia: uma de outro canal, uma de campo e uma em andamento
     * com CIOSP pendente. A de dois dias atrás não entra no recorte do dia.
     */
    private void seedOccurrences() {
        insertOccurrence("CBM_193", "CLOSED", "NOT_REQUIRED", "NOW()");
        insertOccurrence("FIELD_ATTENDED", "CLOSED", "NOT_REQUIRED", "NOW()");
        insertOccurrence("CBM_193", "IN_PROGRESS", "PENDING", "NOW()");
        insertOccurrence("CBM_193", "CLOSED", "NOT_REQUIRED", "NOW() - INTERVAL '2 days'");
    }

    private void insertOccurrence(
            String origin, String status, String ciospStatus, String occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO occurrences
                    (origin, nature, status, occurred_at, beach_id, post_id,
                     reference_text, responder_id, ciosp_status)
                VALUES (?, 'RESCUE', ?, %s, ?, ?, 'Ponto de teste', ?, ?)
                """.formatted(occurredAt),
                origin, status, beachId, postId, lifeguard.userId(), ciospStatus);
    }

    private void seedFlagUpdate() {
        jdbcTemplate.update("""
                INSERT INTO flag_updates (beach_id, flag, set_by, valid_until)
                VALUES (?, 'GREEN', ?, NOW() + INTERVAL '2 hours')
                """, beachId, lifeguard.userId());
        // Lançamento de outra pessoa não entra no contador do guarda-vidas.
        jdbcTemplate.update("""
                INSERT INTO flag_updates (beach_id, flag, set_by, valid_until)
                VALUES (?, 'YELLOW', ?, NOW() + INTERVAL '2 hours')
                """, beachId, coordinator.userId());
    }

    private void openShiftWithPosition(String accessToken, double latitude, double longitude) {
        ResponseEntity<String> opened = exchange(
                "/api/v1/dispatch/shifts",
                HttpMethod.POST,
                Map.of(
                        "postId", postId.toString(),
                        "latitude", latitude,
                        "longitude", longitude,
                        "accuracyMeters", 9.5),
                accessToken);
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode onlyMember(ResponseEntity<String> response) throws Exception {
        JsonNode members = members(response);
        assertThat(members).hasSize(1);
        return members.get(0);
    }

    private JsonNode members(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("generatedAt").isNull()).isFalse();
        return body.get("members");
    }

    private ResponseEntity<String> team(String accessToken) {
        return exchange("/api/v1/management/team", HttpMethod.GET, null, accessToken);
    }

    private ResponseEntity<String> exchange(
            String path, HttpMethod method, Map<String, Object> body, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        try {
            String payload = body == null ? null : objectMapper.writeValueAsString(body);
            return restTemplate.exchange(
                    path, method, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Falha ao montar a requisição de teste", exception);
        }
    }
}
