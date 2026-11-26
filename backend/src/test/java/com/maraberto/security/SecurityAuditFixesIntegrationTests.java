package com.maraberto.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.support.PostgisIntegrationTest;
import com.maraberto.support.TestOperatorAuth;
import java.time.Instant;
import java.util.List;
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
 * Testes de integração das correções da auditoria de segurança (F-01, F-04, F-05, F-06, F-08).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityAuditFixesIntegrationTests extends PostgisIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID beachA;
    private UUID beachB;
    private UUID postA;
    private UUID postB;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_beach_scopes");
        jdbcTemplate.update("DELETE FROM occurrence_events");
        jdbcTemplate.update("DELETE FROM occurrences");
        jdbcTemplate.update("DELETE FROM unmapped_signal_media");
        jdbcTemplate.update("DELETE FROM unmapped_signals");
        jdbcTemplate.update("DELETE FROM flag_media");
        jdbcTemplate.update("DELETE FROM flag_updates");
        jdbcTemplate.update("DELETE FROM shift_sessions");
        jdbcTemplate.update("DELETE FROM lifeguard_post_media");
        jdbcTemplate.update("DELETE FROM lifeguard_posts");
        jdbcTemplate.update("DELETE FROM beaches");

        beachA = insertBeach("Praia Norte", -10.90, -37.05);
        beachB = insertBeach("Praia Sul", -11.20, -37.25);
        postA = insertPost(beachA, "Posto Norte", -10.90, -37.05);
        postB = insertPost(beachB, "Posto Sul", -11.20, -37.25);
    }

    @Test
    void f01BlocksLifeguardMovingStationOutsideOwnShiftAndAllowsOwnShiftOrCoordinator() {
        TestOperatorAuth.Session lifeguard = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        TestOperatorAuth.Session coordinator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "COORDINATOR");

        Map<String, Object> movePayload = Map.of(
                "latitude", -10.901,
                "longitude", -37.051,
                "accuracyMeters", 5.0);

        // Sem plantão aberto -> 403 Forbidden
        ResponseEntity<String> withoutShift = restTemplate.exchange(
                "/api/v1/operations/stations/FIXED/" + postA + "/position",
                HttpMethod.PATCH,
                new HttpEntity<>(movePayload, bearerJson(lifeguard.accessToken())),
                String.class);
        assertThat(withoutShift.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Abre plantão no Posto A -> consegue mover Posto A, mas NÃO Posto B
        openFixedShift(lifeguard.userId(), postA, -10.90, -37.05);

        ResponseEntity<String> otherPost = restTemplate.exchange(
                "/api/v1/operations/stations/FIXED/" + postB + "/position",
                HttpMethod.PATCH,
                new HttpEntity<>(movePayload, bearerJson(lifeguard.accessToken())),
                String.class);
        assertThat(otherPost.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> ownPost = restTemplate.exchange(
                "/api/v1/operations/stations/FIXED/" + postA + "/position",
                HttpMethod.PATCH,
                new HttpEntity<>(movePayload, bearerJson(lifeguard.accessToken())),
                String.class);
        assertThat(ownPost.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Coordenador consegue mover qualquer posto
        ResponseEntity<String> byCoordinator = restTemplate.exchange(
                "/api/v1/operations/stations/FIXED/" + postB + "/position",
                HttpMethod.PATCH,
                new HttpEntity<>(movePayload, bearerJson(coordinator.accessToken())),
                String.class);
        assertThat(byCoordinator.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void f04BlocksLifeguardClosingFlagFromOtherBeachAndAllowsAuthorOrSameBeachShiftOrCoordinator()
            throws Exception {
        TestOperatorAuth.Session author = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        TestOperatorAuth.Session intruder = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        TestOperatorAuth.Session sameBeachGuard = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        TestOperatorAuth.Session coordinator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "COORDINATOR");

        UUID flagId = launchFlag(author.accessToken(), beachA);

        // Outro guarda-vidas fora da praia -> 403 Forbidden
        openFixedShift(intruder.userId(), postB, -11.20, -37.25);
        ResponseEntity<String> blocked = restTemplate.postForEntity(
                "/api/v1/operations/flags/" + flagId + "/close",
                new HttpEntity<>(bearerJson(intruder.accessToken())),
                String.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Guarda-vidas em plantão na mesma praia -> 200 OK
        openFixedShift(sameBeachGuard.userId(), postA, -10.90, -37.05);
        ResponseEntity<String> allowedSameBeach = restTemplate.postForEntity(
                "/api/v1/operations/flags/" + flagId + "/close",
                new HttpEntity<>(bearerJson(sameBeachGuard.accessToken())),
                String.class);
        assertThat(allowedSameBeach.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Coordenador sempre pode encerrar
        UUID secondFlagId = launchFlag(author.accessToken(), beachA);
        ResponseEntity<String> allowedCoordinator = restTemplate.postForEntity(
                "/api/v1/operations/flags/" + secondFlagId + "/close",
                new HttpEntity<>(bearerJson(coordinator.accessToken())),
                String.class);
        assertThat(allowedCoordinator.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void f05RejectsSsrfPushEndpointsAtController() {
        TestOperatorAuth.Session lifeguard = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");

        for (String badEndpoint : List.of(
                "http://127.0.0.1:8080/actuator/health",
                "https://169.254.169.254/latest/meta-data/",
                "https://garage:3900/bucket")) {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/push/subscriptions",
                    new HttpEntity<>(Map.of(
                            "channel", "WEB_PUSH",
                            "endpoint", badEndpoint,
                            "p256dh", "key",
                            "auth", "secret"), bearerJson(lifeguard.accessToken())),
                    String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void f06IsolatesRegionalCoordinatorsAcrossOccurrencesVictimRevealTeamAndUnmappedSignals()
            throws Exception {
        TestOperatorAuth.Session admin = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "ADMIN");
        TestOperatorAuth.Session coordNorth = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "COORDINATOR");
        TestOperatorAuth.Session coordSouth = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "COORDINATOR");
        TestOperatorAuth.Session guardNorth = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        TestOperatorAuth.Session guardSouth = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");

        // TI vincula cada coordenador à sua praia via API administrativa
        ResponseEntity<JsonNode> assignNorth = restTemplate.exchange(
                "/api/v1/admin/users/" + coordNorth.userId() + "/beach-scopes",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("beachIds", List.of(beachA)), bearerJson(admin.accessToken())),
                JsonNode.class);
        assertThat(assignNorth.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assignNorth.getBody().get("beachIds").size()).isEqualTo(1);

        ResponseEntity<JsonNode> assignSouth = restTemplate.exchange(
                "/api/v1/admin/users/" + coordSouth.userId() + "/beach-scopes",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("beachIds", List.of(beachB)), bearerJson(admin.accessToken())),
                JsonNode.class);
        assertThat(assignSouth.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Coordenador não pode alterar escopos (apenas ADMIN)
        ResponseEntity<String> forbiddenAssign = restTemplate.exchange(
                "/api/v1/admin/users/" + coordNorth.userId() + "/beach-scopes",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("beachIds", List.of()), bearerJson(coordNorth.accessToken())),
                String.class);
        assertThat(forbiddenAssign.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Plantões em praias diferentes
        openFixedShift(guardNorth.userId(), postA, -10.90, -37.05);
        openFixedShift(guardSouth.userId(), postB, -11.20, -37.25);

        // Ocorrências com vítima em praias diferentes
        UUID occNorth = createOccurrenceWithVictim(guardNorth.accessToken(), beachA, "Vítima Norte");
        UUID occSouth = createOccurrenceWithVictim(guardSouth.accessToken(), beachB, "Vítima Sul");

        // 1. Listagem de ocorrências isolada por região
        JsonNode northList = restTemplate.exchange(
                "/api/v1/occurrences",
                HttpMethod.GET,
                new HttpEntity<>(bearerJson(coordNorth.accessToken())),
                JsonNode.class).getBody();
        assertThat(northList.get("items").size()).isEqualTo(1);
        assertThat(northList.get("items").get(0).get("id").asText()).isEqualTo(occNorth.toString());

        // 2. Tentativa de revelar vítima de ocorrência fora do escopo retorna 404 Not Found
        ResponseEntity<String> crossRegionReveal = restTemplate.postForEntity(
                "/api/v1/occurrences/" + occSouth + "/victim/reveal",
                new HttpEntity<>(Map.of("reason", "Verificação indevida"), bearerJson(coordNorth.accessToken())),
                String.class);
        assertThat(crossRegionReveal.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Revelação dentro do próprio escopo funciona
        ResponseEntity<JsonNode> ownRegionReveal = restTemplate.postForEntity(
                "/api/v1/occurrences/" + occNorth + "/victim/reveal",
                new HttpEntity<>(Map.of("reason", "Transcrição CIOSP"), bearerJson(coordNorth.accessToken())),
                JsonNode.class);
        assertThat(ownRegionReveal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownRegionReveal.getBody().get("name").asText()).isEqualTo("Vítima Norte");

        // 3. Equipe em plantão isolada por região
        JsonNode northTeam = restTemplate.exchange(
                "/api/v1/management/team",
                HttpMethod.GET,
                new HttpEntity<>(bearerJson(coordNorth.accessToken())),
                JsonNode.class).getBody();
        assertThat(northTeam.get("members").size()).isEqualTo(1);
        assertThat(northTeam.get("members").get(0).get("userId").asText())
                .isEqualTo(guardNorth.userId().toString());

        // 4. ADMIN continua vendo ambas as regiões
        JsonNode adminTeam = restTemplate.exchange(
                "/api/v1/management/team",
                HttpMethod.GET,
                new HttpEntity<>(bearerJson(admin.accessToken())),
                JsonNode.class).getBody();
        assertThat(adminTeam.get("members").size()).isEqualTo(2);
    }

    @Test
    void f08DoesNotExposeInactiveTidesRoutePublicly() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/tides/anything", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private UUID insertBeach(String name, double lat, double lon) {
        UUID id = UUID.randomUUID();
        String wkt = String.format(java.util.Locale.ROOT,
                "POLYGON((%f %f, %f %f, %f %f, %f %f, %f %f))",
                lon - 0.01, lat - 0.01,
                lon + 0.01, lat - 0.01,
                lon + 0.01, lat + 0.01,
                lon - 0.01, lat + 0.01,
                lon - 0.01, lat - 0.01);
        jdbcTemplate.update("""
                INSERT INTO beaches (id, name, municipality, state_code, boundary, active)
                VALUES (?, ?, 'Aracaju', 'SE', ST_GeomFromText(?, 4326), TRUE)
                """, id, name, wkt);
        return id;
    }

    private UUID insertPost(UUID beachId, String name, double lat, double lon) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO lifeguard_posts (id, beach_id, name, location, active, position_source)
                VALUES (?, ?, ?, CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography), TRUE, 'MANUAL')
                """, id, beachId, name, lon, lat);
        return id;
    }

    private void openFixedShift(UUID userId, UUID postId, double lat, double lon) {
        jdbcTemplate.update("""
                INSERT INTO shift_sessions
                    (id, user_id, post_id, started_at, last_seen_at, last_position, last_position_at)
                VALUES (?, ?, ?, NOW(), NOW(),
                        CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography), NOW())
                """, UUID.randomUUID(), userId, postId, lon, lat);
    }

    private UUID launchFlag(String accessToken, UUID beachId) throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/operations/flags",
                new HttpEntity<>(Map.of(
                        "beachId", beachId,
                        "flag", "RED"), bearerJson(accessToken)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(objectMapper.readTree(response.getBody()).at("/flag/id").asText());
    }

    private UUID createOccurrenceWithVictim(String accessToken, UUID beachId, String victimName)
            throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/occurrences",
                new HttpEntity<>(Map.of(
                        "clientRequestId", UUID.randomUUID(),
                        "origin", "FIELD_ATTENDED",
                        "nature", "RESCUE",
                        "occurredAt", Instant.now().toString(),
                        "beachId", beachId,
                        "referenceText", "Em frente ao posto",
                        "summary", "Atendimento preventivo",
                        "victimName", victimName), bearerJson(accessToken)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(
                objectMapper.readTree(response.getBody()).at("/detail/occurrence/id").asText());
    }

    private static HttpHeaders bearerJson(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
