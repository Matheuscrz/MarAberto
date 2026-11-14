package com.maraberto.operations;

import static com.maraberto.support.KmlFixtures.feature;
import static com.maraberto.support.KmlFixtures.kml;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.beaches.application.BathingAreaImportService;
import com.maraberto.beaches.application.BathingAreaImportService.ImportCommand;
import com.maraberto.beaches.application.ImportMode;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.support.PostgisIntegrationTest;
import com.maraberto.support.TestOperatorAuth;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * Estruturas e sinalização de ponta a ponta: rotas públicas, papéis e
 * vigência aplicada na leitura (RF01/RF12/RF38).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicOperationsIntegrationTests extends PostgisIntegrationTest {

    private static final String PREFIX = "se-areas-banho:";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private BathingAreaImportService importService;

    private TestOperatorAuth.Session lifeguard;
    private TestOperatorAuth.Session admin;
    private UUID beachId;
    private UUID postId;
    private UUID mobileBaseId;

    @BeforeEach
    void setUp() {
        clean();
        lifeguard = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        admin = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "ADMIN");
        beachId = importBeach();
        postId = insertPost();
        mobileBaseId = insertMobileBase();
        openShiftOnBase();
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM flag_updates");
        jdbcTemplate.update("DELETE FROM shift_sessions");
        jdbcTemplate.update("DELETE FROM lifeguard_posts WHERE name LIKE 'Posto Teste%'");
        jdbcTemplate.update("DELETE FROM mobile_bases WHERE name LIKE 'Base Teste%'");
        jdbcTemplate.update("DELETE FROM beach_sectors WHERE external_id LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM import_batches WHERE state_code = 'SE'");
        jdbcTemplate.update("DELETE FROM beaches WHERE external_id LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM beaches WHERE name LIKE 'Praia Teste%'");
    }

    @Test
    void thePublicMapShowsTheActivePostAndTheLiveMobileBase() throws Exception {
        JsonNode stations = publicStations();

        JsonNode fixed = findStationByName(stations, "Posto Teste Fixo");
        JsonNode mobile = findStationByName(stations, "Base Teste Móvel");

        assertThat(fixed).isNotNull();
        assertThat(mobile).isNotNull();
        assertThat(fixed.get("name").asText()).isEqualTo("Posto Teste Fixo");
        assertThat(fixed.get("beachName").asText()).isEqualTo("Praia Teste");
        assertThat(mobile.get("name").asText()).isEqualTo("Base Teste Móvel");
        // A base usa a última posição do plantão, não a posição cadastrada.
        assertThat(mobile.get("latitude").asDouble()).isEqualTo(-10.030, within(0.0001));
        assertThat(mobile.get("longitude").asDouble()).isEqualTo(-35.030, within(0.0001));
    }

    @Test
    void aDeactivatedPostLeavesThePublicMapButStaysInTheAdminList() throws Exception {
        ResponseEntity<String> patched = restTemplate.exchange(
                "/api/v1/admin/stations/FIXED/" + postId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("active", false, "statusNote", "Em reforma"),
                        bearer(admin)),
                String.class);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(patched.getBody()).get("active").asBoolean()).isFalse();

        assertThat(findStationByName(publicStations(), "Posto Teste Fixo")).isNull();
        assertThat(findStationByName(publicStations(), "Base Teste Móvel")).isNotNull();

        JsonNode adminList = adminStations();
        JsonNode fixed = findStationByName(adminList, "Posto Teste Fixo");
        assertThat(fixed.get("active").asBoolean()).isFalse();
        assertThat(fixed.get("statusNote").asText()).isEqualTo("Em reforma");
        assertThat(fixed.get("deactivatedAt").isNull()).isFalse();
    }

    @Test
    void reactivatingAStationKeepsTheDeactivationNote() throws Exception {
        restTemplate.exchange(
                "/api/v1/admin/stations/FIXED/" + postId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("active", false, "statusNote", "Em reforma"),
                        bearer(admin)),
                String.class);

        restTemplate.exchange(
                "/api/v1/admin/stations/FIXED/" + postId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("active", true), bearer(admin)),
                String.class);

        JsonNode fixed = findStationByName(adminStations(), "Posto Teste Fixo");
        assertThat(fixed.get("active").asBoolean()).isTrue();
        assertThat(fixed.get("deactivatedAt").isNull()).isTrue();
        assertThat(fixed.get("statusNote").asText()).isEqualTo("Em reforma");
    }

    @Test
    void aLifeguardCannotChangeTheStationRegistry() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/stations/FIXED/" + postId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("active", false), bearer(lifeguard)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aMobileBaseWithoutALiveShiftDisappearsFromTheMap() throws Exception {
        jdbcTemplate.update("""
                UPDATE shift_sessions
                   SET last_seen_at = NOW() - INTERVAL '20 minutes'
                 WHERE mobile_base_id = ?
                """, mobileBaseId);

        assertThat(findStationByName(publicStations(), "Base Teste Móvel")).isNull();
    }

    @Test
    void theFieldConsoleListsStructuresEvenWhenTheMobileBaseIsOffTheMap() throws Exception {
        jdbcTemplate.update("""
                UPDATE shift_sessions
                   SET last_seen_at = NOW() - INTERVAL '20 minutes'
                 WHERE mobile_base_id = ?
                """, mobileBaseId);
        jdbcTemplate.update("""
                UPDATE mobile_bases
                   SET position_updated_at = NOW(), position_accuracy_m = 12.5
                 WHERE id = ?
                """, mobileBaseId);

        JsonNode stations = publicJson("/api/v1/operations/stations", lifeguard);

        assertThat(findStationByName(publicStations(), "Base Teste Móvel")).isNull();
        JsonNode mobile = findStationByName(stations, "Base Teste Móvel");
        assertThat(mobile).as("base sem plantão vivo continua no console de campo").isNotNull();
        assertThat(mobile.get("active").asBoolean()).isTrue();
        // A lista de campo usa a posição cadastrada da estrutura, não a do turno.
        assertThat(mobile.get("latitude").asDouble()).isEqualTo(-10.040, within(0.0001));
        assertThat(mobile.get("longitude").asDouble()).isEqualTo(-35.040, within(0.0001));
        assertThat(mobile.get("positionAccuracyM").asDouble()).isEqualTo(12.5);
        assertThat(mobile.get("positionUpdatedAt").isNull()).isFalse();
        assertThat(findStationByName(stations, "Posto Teste Fixo")).isNotNull();
    }

    @Test
    void theFieldConsoleIsNotPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/operations/stations", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aLifeguardLaunchesAFlagAndThePublicMapOnlyShowsItWhileItIsValid() throws Exception {
        ResponseEntity<String> launched = restTemplate.exchange(
                "/api/v1/operations/flags",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "beachId", beachId.toString(),
                        "flag", "RED",
                        "notes", "Corrente de retorno na foz do rio"), bearer(lifeguard)),
                String.class);

        assertThat(launched.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID flagId = UUID.fromString(
                objectMapper.readTree(launched.getBody()).get("flag").get("id").asText());

        JsonNode flags = publicJson("/api/v1/flags");
        assertThat(flags).hasSize(1);
        assertThat(flags.get(0).get("flag").asText()).isEqualTo("RED");
        assertThat(flags.get(0).get("beachName").asText()).isEqualTo("Praia Teste");
        assertThat(flags.get(0).get("notes").asText())
                .isEqualTo("Corrente de retorno na foz do rio");

        // Fecha antes da validade: some do mapa, continua no histórico.
        ResponseEntity<String> closed = restTemplate.exchange(
                "/api/v1/operations/flags/" + flagId + "/close",
                HttpMethod.POST,
                new HttpEntity<>(bearer(lifeguard)),
                String.class);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(publicJson("/api/v1/flags")).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flag_updates", Integer.class)).isEqualTo(1);
    }

    @Test
    void anExpiredFlagIsNotPublished() throws Exception {
        restTemplate.exchange(
                "/api/v1/operations/flags",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("beachId", beachId.toString(), "flag", "YELLOW"),
                        bearer(lifeguard)),
                String.class);

        jdbcTemplate.update("""
                UPDATE flag_updates
                   SET created_at = NOW() - INTERVAL '2 hours',
                       valid_until = NOW() - INTERVAL '1 minute'
                """);

        assertThat(publicJson("/api/v1/flags")).isEmpty();
    }

    @Test
    void theBeachDetailStillShowsTheLastFlagAfterItExpires() throws Exception {
        restTemplate.exchange(
                "/api/v1/operations/flags",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("beachId", beachId.toString(), "flag", "RED"),
                        bearer(lifeguard)),
                String.class);

        jdbcTemplate.update("""
                UPDATE flag_updates
                   SET created_at = NOW() - INTERVAL '2 hours',
                       valid_until = NOW() - INTERVAL '1 minute'
                """);

        // No mapa, nada: a bandeira venceu.
        assertThat(publicJson("/api/v1/flags?beachId=" + beachId)).isEmpty();

        // Na ficha, a última cor com a data de validade vencida.
        JsonNode latest = publicJson(
                "/api/v1/flags?beachId=" + beachId + "&includeExpired=true");
        assertThat(latest).hasSize(1);
        assertThat(latest.get(0).get("flag").asText()).isEqualTo("RED");
        assertThat(latest.get(0).get("closedAt").isNull()).isTrue();

        ResponseEntity<String> withoutBeach = restTemplate.getForEntity(
                "/api/v1/flags?includeExpired=true", String.class);
        assertThat(withoutBeach.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void launchingAFlagRequiresAnAuthenticatedOperator() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/operations/flags",
                new HttpEntity<>(Map.of("beachId", beachId.toString(), "flag", "GREEN"),
                        new HttpHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private JsonNode publicStations() throws Exception {
        return publicJson("/api/v1/stations");
    }

    private JsonNode adminStations() throws Exception {
        return publicJson("/api/v1/admin/stations", admin);
    }

    private JsonNode publicJson(String path) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode publicJson(String path, TestOperatorAuth.Session session) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(bearer(session)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode findStationByName(JsonNode array, String name) {
        for (JsonNode node : array) {
            if (node.get("name").asText().equals(name)) {
                return node;
            }
        }
        return null;
    }

    private static HttpHeaders bearer(TestOperatorAuth.Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }

    private UUID importBeach() {
        String file = kml(feature("1", "Praia Teste", "Setor Único", "Aracaju", null,
                -35.010, -10.010));
        importService.importKml(new ImportCommand(
                file.getBytes(StandardCharsets.UTF_8), "areas.kml", "SE", false,
                ImportMode.UPSERT, null));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, PREFIX + "area:1");
    }

    private UUID insertPost() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO lifeguard_posts (id, beach_id, name, location)
                        VALUES (?, ?, 'Posto Teste Fixo',
                                CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography))
                        """, id, beachId, -35.010, -10.010);
        return id;
    }

    private UUID insertMobileBase() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO mobile_bases (id, name, location)
                        VALUES (?, 'Base Teste Móvel',
                                CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography))
                        """, id, -35.040, -10.040);
        return id;
    }

    private void openShiftOnBase() {
        jdbcTemplate.update("""
                        INSERT INTO shift_sessions
                            (user_id, mobile_base_id, started_at, last_seen_at,
                             last_position, last_position_at)
                        VALUES (?, ?, NOW(), NOW(),
                                CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography),
                                NOW())
                        """, lifeguard.userId(), mobileBaseId, -35.030, -10.030);
    }
}
