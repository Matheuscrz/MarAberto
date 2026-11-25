package com.maraberto.occurrences;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.support.PostgisIntegrationTest;
import com.maraberto.support.TestOperatorAuth;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

/**
 * Registro manual de ocorrências externas e em campo (RF33, RN11, RN12, RN13, ADR-013).
 *
 * <p>Cada teste usa uma área de banho própria e distante das demais para não
 * interferir nas buscas espaciais de outras classes na mesma JVM.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "maraberto.emergency.outbox.sweep-initial-delay=PT1H",
                "maraberto.dispatch.max-recipients=3"
        })
class ManualOccurrenceTests extends PostgisIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthenticationService authenticationService;

    @Test
    void registersAllManualOriginsWithCoordinatesOrBeachAndReference() throws Exception {
        Area area = createArea(-11.400, -37.400, "Setor Sul");
        TestOperatorAuth.Session guard = lifeguardAt(area, "LIFEGUARD");

        // 1. FIELD_ATTENDED com coordenada e já encerrada com RESOLVED
        UUID fieldRequestId = UUID.randomUUID();
        String fieldBody = """
                {
                  "clientRequestId": "%s",
                  "origin": "FIELD_ATTENDED",
                  "nature": "RESCUE",
                  "occurredAt": "%s",
                  "latitude": %s,
                  "longitude": %s,
                  "accuracyMeters": 8.5,
                  "summary": "Resgate de banhista em corrente de retorno.",
                  "outcome": "RESOLVED"
                }
                """.formatted(
                fieldRequestId,
                Instant.now().minus(5, ChronoUnit.MINUTES),
                area.centerLatitude(),
                area.centerLongitude());

        ResponseEntity<String> fieldResponse = postOccurrence(guard.accessToken(), fieldBody);
        assertThat(fieldResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode fieldJson = objectMapper.readTree(fieldResponse.getBody());
        JsonNode fieldOcc = fieldJson.get("detail").get("occurrence");
        assertThat(fieldOcc.get("origin").asText()).isEqualTo("FIELD_ATTENDED");
        assertThat(fieldOcc.get("status").asText()).isEqualTo("CLOSED");
        assertThat(fieldOcc.get("outcome").asText()).isEqualTo("RESOLVED");
        assertThat(fieldOcc.get("ciospStatus").asText()).isEqualTo("PENDING");
        assertThat(fieldOcc.get("beachId").asText()).isEqualTo(area.beachId().toString());
        assertThat(fieldOcc.get("sectorId").asText()).isEqualTo(area.sectorId().toString());
        assertThat(fieldOcc.get("responderId").asText()).isEqualTo(guard.userId().toString());
        assertThat(fieldOcc.get("closedAt").isNull()).isFalse();

        // 2. CBM_193 sem coordenada, usando praia + referência, ainda em aberto
        String cbmBody = """
                {
                  "clientRequestId": "%s",
                  "origin": "CBM_193",
                  "nature": "DROWNING",
                  "occurredAt": "%s",
                  "beachId": "%s",
                  "referenceText": "Em frente ao posto 02",
                  "externalProtocol": "193-2026-9981",
                  "summary": "Chamado repassado pelo 193, equipe deslocada."
                }
                """.formatted(
                UUID.randomUUID(),
                Instant.now().minus(2, ChronoUnit.MINUTES),
                area.beachId());

        ResponseEntity<String> cbmResponse = postOccurrence(guard.accessToken(), cbmBody);
        assertThat(cbmResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode cbmDetail = objectMapper.readTree(cbmResponse.getBody()).get("detail");
        assertThat(cbmDetail.get("occurrence").get("origin").asText()).isEqualTo("CBM_193");
        assertThat(cbmDetail.get("occurrence").get("status").asText()).isEqualTo("OPEN");
        assertThat(cbmDetail.get("occurrence").get("ciospStatus").asText()).isEqualTo("PENDING");
        assertThat(cbmDetail.get("occurrence").get("latitude").isNull()).isTrue();
        assertThat(cbmDetail.get("externalProtocol").asText()).isEqualTo("193-2026-9981");
        assertThat(cbmDetail.get("referenceText").asText()).isEqualTo("Em frente ao posto 02");

        // 3. EXTERNAL_OTHER encerrado como FALSE_ALARM -> CIOSP NOT_REQUIRED
        String externalBody = """
                {
                  "clientRequestId": "%s",
                  "origin": "EXTERNAL_OTHER",
                  "nature": "OTHER",
                  "occurredAt": "%s",
                  "beachId": "%s",
                  "referenceText": "Quiosque central",
                  "summary": "Chamado via rádio sem confirmação no local.",
                  "outcome": "FALSE_ALARM"
                }
                """.formatted(
                UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.MINUTES),
                area.beachId());

        ResponseEntity<String> externalResponse = postOccurrence(guard.accessToken(), externalBody);
        assertThat(externalResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode extOcc = objectMapper.readTree(externalResponse.getBody())
                .get("detail").get("occurrence");
        assertThat(extOcc.get("origin").asText()).isEqualTo("EXTERNAL_OTHER");
        assertThat(extOcc.get("status").asText()).isEqualTo("FALSE_ALARM");
        assertThat(extOcc.get("ciospStatus").asText()).isEqualTo("NOT_REQUIRED");
    }

    @Test
    void clientRequestIdGuaranteesIdempotencyWithoutDuplicatingEvents() throws Exception {
        Area area = createArea(-11.600, -37.600, "Setor Norte");
        TestOperatorAuth.Session guard = lifeguardAt(area, "LIFEGUARD");

        UUID clientRequestId = UUID.randomUUID();
        String body = """
                {
                  "clientRequestId": "%s",
                  "origin": "FIELD_ATTENDED",
                  "nature": "FIRST_AID",
                  "occurredAt": "%s",
                  "beachId": "%s",
                  "referenceText": "Próximo à guarita",
                  "summary": "Atendimento de primeiros socorros por corte no pé."
                }
                """.formatted(clientRequestId, Instant.now(), area.beachId());

        ResponseEntity<String> first = postOccurrence(guard.accessToken(), body);
        ResponseEntity<String> second = postOccurrence(guard.accessToken(), body);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode firstOcc = objectMapper.readTree(first.getBody()).get("detail").get("occurrence");
        JsonNode secondOcc = objectMapper.readTree(second.getBody()).get("detail").get("occurrence");

        assertThat(secondOcc.get("id").asText()).isEqualTo(firstOcc.get("id").asText());
        assertThat(secondOcc.get("number").asLong()).isEqualTo(firstOcc.get("number").asLong());

        UUID occurrenceId = UUID.fromString(firstOcc.get("id").asText());
        Integer openedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM occurrence_events WHERE occurrence_id = ? AND kind = 'OPENED'",
                Integer.class,
                occurrenceId);
        assertThat(openedEvents).isEqualTo(1);
    }

    @Test
    void validatesLocationAndRejectsSosOrigin() throws Exception {
        Area area = createArea(-11.800, -37.800, "Setor Leste");
        TestOperatorAuth.Session guard = lifeguardAt(area, "LIFEGUARD");

        // Sem coordenada e sem referência
        String missingPlace = """
                {
                  "clientRequestId": "%s",
                  "origin": "CBM_193",
                  "nature": "RESCUE",
                  "occurredAt": "%s",
                  "beachId": "%s",
                  "summary": "Falta ponto de referência."
                }
                """.formatted(UUID.randomUUID(), Instant.now(), area.beachId());
        assertThat(postOccurrence(guard.accessToken(), missingPlace).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Latitude sem longitude
        String partialCoord = """
                {
                  "clientRequestId": "%s",
                  "origin": "FIELD_ATTENDED",
                  "nature": "RESCUE",
                  "occurredAt": "%s",
                  "latitude": %s,
                  "summary": "Coordenada incompleta."
                }
                """.formatted(UUID.randomUUID(), Instant.now(), area.centerLatitude());
        assertThat(postOccurrence(guard.accessToken(), partialCoord).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Origem SOS_CITIZEN não permitida no registro manual
        String sosOrigin = """
                {
                  "clientRequestId": "%s",
                  "origin": "SOS_CITIZEN",
                  "nature": "RESCUE",
                  "occurredAt": "%s",
                  "latitude": %s,
                  "longitude": %s,
                  "summary": "Tentativa com origem SOS."
                }
                """.formatted(
                UUID.randomUUID(),
                Instant.now(),
                area.centerLatitude(),
                area.centerLongitude());
        assertThat(postOccurrence(guard.accessToken(), sosOrigin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void warnsAboutNearbyDuplicatesWithin200mAnd30MinWithoutBlocking() throws Exception {
        Area area = createArea(-12.000, -38.000, "Setor Centro");
        TestOperatorAuth.Session guard = lifeguardAt(area, "LIFEGUARD");

        // Cria um SOS pendente no centro da área
        UUID callId = createEmergencyCall(area.centerLatitude(), area.centerLongitude());

        // Cria ocorrência manual a ~55 metros (0.0005 graus de latitude ≈ 55 m) e 5 min depois
        double nearbyLat = area.centerLatitude() + 0.0005;
        String nearbyBody = """
                {
                  "clientRequestId": "%s",
                  "origin": "CBM_193",
                  "nature": "RESCUE",
                  "occurredAt": "%s",
                  "latitude": %s,
                  "longitude": %s,
                  "summary": "Chamado 193 no mesmo ponto do SOS."
                }
                """.formatted(
                UUID.randomUUID(),
                Instant.now().plus(5, ChronoUnit.MINUTES),
                nearbyLat,
                area.centerLongitude());

        ResponseEntity<String> nearbyResponse = postOccurrence(guard.accessToken(), nearbyBody);
        assertThat(nearbyResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode nearbyJson = objectMapper.readTree(nearbyResponse.getBody());
        JsonNode duplicates = nearbyJson.get("possibleDuplicates");
        assertThat(duplicates.isArray()).isTrue();
        assertThat(duplicates).isNotEmpty();
        assertThat(duplicates.get(0).get("kind").asText()).isEqualTo("EMERGENCY_CALL");
        assertThat(duplicates.get(0).get("id").asText()).isEqualTo(callId.toString());
        assertThat(duplicates.get(0).get("distanceMeters").asDouble()).isLessThan(200.0);

        // Ocorrência fora do raio (> 200 m, ~550 m) não deve acusar duplicata
        double farLat = area.centerLatitude() + 0.005;
        String farBody = """
                {
                  "clientRequestId": "%s",
                  "origin": "FIELD_ATTENDED",
                  "nature": "MARINE_ANIMAL",
                  "occurredAt": "%s",
                  "latitude": %s,
                  "longitude": %s,
                  "summary": "Atendimento distante de qualquer outro registro."
                }
                """.formatted(UUID.randomUUID(), Instant.now(), farLat, area.centerLongitude());

        ResponseEntity<String> farResponse = postOccurrence(guard.accessToken(), farBody);
        assertThat(farResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(objectMapper.readTree(farResponse.getBody()).get("possibleDuplicates"))
                .isEmpty();
    }

    private ResponseEntity<String> postOccurrence(String accessToken, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return restTemplate.postForEntity(
                "/api/v1/occurrences", new HttpEntity<>(body, headers), String.class);
    }

    private UUID createEmergencyCall(double latitude, double longitude) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {
                  "clientRequestId": "%s",
                  "latitude": %s,
                  "longitude": %s,
                  "accuracyMeters": 10.0
                }
                """.formatted(UUID.randomUUID(), latitude, longitude);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/emergency-calls", new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(
                objectMapper.readTree(response.getBody()).get("callId").asText());
    }

    private TestOperatorAuth.Session lifeguardAt(Area area, String role) {
        TestOperatorAuth.Session session = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, role);
        UUID shiftId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shift_sessions
                    (id, user_id, post_id, beach_id, started_at,
                     last_position, last_position_at)
                VALUES (?, ?, ?, ?, NOW(),
                        CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography), NOW())
                """,
                shiftId, session.userId(), area.postId(), area.beachId(),
                area.centerLongitude(), area.centerLatitude());
        return session;
    }

    private Area createArea(double centerLatitude, double centerLongitude, String sectorName) {
        UUID beachId = UUID.randomUUID();
        String beachName = "Área manual " + beachId.toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO beaches (id, name, state_code, boundary)
                VALUES (?, ?, 'SE', ST_Multi(ST_GeomFromText(?, 4326)))
                """, beachId, beachName, square(centerLatitude, centerLongitude, 0.05, 0.1));

        UUID sectorId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO beach_sectors
                    (id, beach_id, external_id, name, display_order, geometry, geometry_hash)
                VALUES (?, ?, ?, ?, 1, ST_Multi(ST_GeomFromText(?, 4326)), ?)
                """, sectorId, beachId, "SE-manual:" + sectorId, sectorName,
                square(centerLatitude, centerLongitude, 0.02, 0.05), "hash-" + sectorId);

        UUID postId = UUID.randomUUID();
        String postName = "Posto manual " + postId.toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO lifeguard_posts
                    (id, beach_id, name, location, active, coverage_radius_m)
                VALUES (?, ?, ?, CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography),
                        TRUE, 50000)
                """, postId, beachId, postName, centerLongitude, centerLatitude);

        return new Area(beachId, sectorId, postId, centerLatitude, centerLongitude);
    }

    private static String square(
            double centerLatitude, double centerLongitude,
            double latitudeRadius, double longitudeRadius) {
        double minLat = centerLatitude - latitudeRadius;
        double maxLat = centerLatitude + latitudeRadius;
        double minLon = centerLongitude - longitudeRadius;
        double maxLon = centerLongitude + longitudeRadius;
        return "POLYGON((%s %s, %s %s, %s %s, %s %s, %s %s))".formatted(
                minLon, minLat, maxLon, minLat, maxLon, maxLat,
                minLon, maxLat, minLon, minLat);
    }

    private record Area(
            UUID beachId,
            UUID sectorId,
            UUID postId,
            double centerLatitude,
            double centerLongitude) {
    }
}
