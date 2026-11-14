package com.maraberto.operations;

import static com.maraberto.support.KmlFixtures.feature;
import static com.maraberto.support.KmlFixtures.kml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.beaches.application.BathingAreaImportService;
import com.maraberto.beaches.application.BathingAreaImportService.ImportCommand;
import com.maraberto.beaches.application.ImportMode;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.support.PostgisIntegrationTest;
import com.maraberto.support.TestOperatorAuth;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Sinalização e estrutura ancoradas na posição do aparelho (RF11/RF12).
 *
 * <p>Cobre as duas formas de lançar a bandeira: escolhendo praia/setor no
 * console (já coberto em {@code FlagLifecycleIntegrationTests}) e enviando a
 * coordenada atual, quando o servidor resolve a praia e o setor. Também cobre a
 * foto normalizada e o cadastro/reposição do posto com o GPS do aparelho.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlagLocationAndMediaTests extends PostgisIntegrationTest {

    private static final String PREFIX = "se-areas-banho:";
    private static final double BEACH_MIN_X = -35.120;
    private static final double BEACH_MIN_Y = -10.120;

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

    private UUID beachId;
    private UUID northSectorId;
    private final List<UUID> createdStations = new ArrayList<>();

    @BeforeEach
    void cleanState() {
        jdbcTemplate.update("DELETE FROM unmapped_signals");
        jdbcTemplate.update("DELETE FROM flag_updates");
        jdbcTemplate.update("DELETE FROM lifeguard_post_media");
        jdbcTemplate.update("DELETE FROM mobile_base_media");
        jdbcTemplate.update("DELETE FROM media_assets");
        jdbcTemplate.update("DELETE FROM beach_sectors WHERE external_id LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM import_batches WHERE state_code = 'SE'");
        jdbcTemplate.update("DELETE FROM beaches WHERE external_id LIKE ?", PREFIX + "%");
        createdStations.clear();
    }

    @AfterEach
    void removeCreatedStations() {
        for (UUID stationId : createdStations) {
            jdbcTemplate.update(
                    "DELETE FROM shift_sessions WHERE post_id = ? OR mobile_base_id = ?",
                    stationId, stationId);
            jdbcTemplate.update("DELETE FROM lifeguard_posts WHERE id = ?", stationId);
            jdbcTemplate.update("DELETE FROM mobile_bases WHERE id = ?", stationId);
        }
    }

    @Test
    void uploadingAPhotoNormalizesItAndServesItWithAnETag() throws Exception {
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");

        ResponseEntity<String> upload = uploadPhoto(lifeguard.accessToken(), png(40, 20));
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode media = objectMapper.readTree(upload.getBody());
        assertThat(media.get("contentType").asText()).isEqualTo("image/jpeg");
        assertThat(media.get("widthPx").asInt()).isEqualTo(40);
        assertThat(media.get("heightPx").asInt()).isEqualTo(20);
        assertThat(media.get("url").asText()).startsWith("/api/v1/media/");

        // O binário guardado é o JPEG que o servidor produziu, não o PNG enviado.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT object_key, size_bytes, sha256 FROM media_assets WHERE id = ?",
                UUID.fromString(media.get("id").asText()));
        assertThat(row.get("object_key").toString()).startsWith("signals/").endsWith(".jpg");
        assertThat((byte[]) row.get("sha256")).hasSize(32);

        ResponseEntity<byte[]> image = restTemplate.exchange(
                media.get("url").asText(), HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), byte[].class);
        assertThat(image.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(image.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(image.getBody()).isNotNull();
        assertThat(image.getBody()[0] & 0xFF).isEqualTo(0xFF);
        assertThat(image.getBody()[1] & 0xFF).isEqualTo(0xD8);
        String etag = image.getHeaders().getETag();
        assertThat(etag).isNotNull();

        HttpHeaders conditional = new HttpHeaders();
        conditional.setIfNoneMatch(etag);
        ResponseEntity<byte[]> unchanged = restTemplate.exchange(
                media.get("url").asText(), HttpMethod.GET,
                new HttpEntity<>(conditional), byte[].class);
        assertThat(unchanged.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void aPhotoCanAlsoBeSentAsBase64ByTheNativeApp() throws Exception {
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");

        String body = objectMapper.writeValueAsString(
                Map.of("contentBase64", java.util.Base64.getEncoder().encodeToString(png(24, 16))));
        ResponseEntity<String> upload = restTemplate.exchange(
                "/api/v1/media",
                HttpMethod.POST,
                authorized(lifeguard.accessToken(), body),
                String.class);

        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode media = objectMapper.readTree(upload.getBody());
        assertThat(media.get("contentType").asText()).isEqualTo("image/jpeg");
        assertThat(media.get("widthPx").asInt()).isEqualTo(24);

        ResponseEntity<String> invalid = restTemplate.exchange(
                "/api/v1/media",
                HttpMethod.POST,
                authorized(lifeguard.accessToken(), "{\"contentBase64\":\"%%%\"}"),
                String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aFileThatIsNotAnImageIsRejected() {
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");

        ResponseEntity<String> response = uploadPhoto(
                lifeguard.accessToken(), "não é uma foto".getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isNotNull()
                .satisfies(type -> assertThat(
                        type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
    }

    @Test
    void aFlagLaunchedByLocationResolvesTheSectorAndKeepsTheSentPoint() throws Exception {
        importBeach();
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");
        ResponseEntity<String> uploaded = uploadPhoto(lifeguard.accessToken(), png(60, 30));
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String photoId = objectMapper.readTree(uploaded.getBody()).get("id").asText();

        double latitude = BEACH_MIN_Y + 0.0005;
        double longitude = BEACH_MIN_X + 0.0005;

        ResponseEntity<String> response = launch(lifeguard.accessToken(), """
                {
                  "flag": "YELLOW",
                  "latitude": %s,
                  "longitude": %s,
                  "accuracyMeters": 8.0,
                  "notes": "Mar agitado no setor norte",
                  "mediaIds": ["%s"]
                }
                """.formatted(latitude, longitude, photoId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode launched = objectMapper.readTree(response.getBody());
        assertThat(launched.get("outcome").asText()).isEqualTo("PLACED");
        JsonNode publishedFlag = launched.get("flag");
        assertThat(publishedFlag.get("beachId").asText()).isEqualTo(beachId.toString());
        assertThat(publishedFlag.get("sectorId").asText()).isEqualTo(northSectorId.toString());
        assertThat(publishedFlag.get("photoUrls")).hasSize(1);

        ResponseEntity<String> flags = restTemplate.getForEntity(
                "/api/v1/flags", String.class);
        JsonNode published = objectMapper.readTree(flags.getBody()).get(0);
        assertThat(published.get("sectorLabel").asText()).isEqualTo("Setor Norte");
        // O pin é o ponto enviado, não o centroide do setor.
        assertThat(published.get("latitude").asDouble()).isCloseTo(latitude, within(1e-6));
        assertThat(published.get("longitude").asDouble()).isCloseTo(longitude, within(1e-6));
        assertThat(published.get("photoUrls")).hasSize(1);

        // A origem da posição fica registrada para a operação.
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT position_source || ':' || round(position_accuracy_m::numeric, 0)
                          FROM flag_updates WHERE id = ?
                        """, String.class, UUID.fromString(publishedFlag.get("id").asText())))
                .isEqualTo("DEVICE_GPS:8");
    }

    @Test
    void aFlagOutsideTheMappedAreaIsQueuedForTheCoordination() throws Exception {
        importBeach();
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");
        String photoId = objectMapper.readTree(
                        uploadPhoto(lifeguard.accessToken(), png(32, 24)).getBody())
                .get("id").asText();

        // Ponto logo fora do polígono importado: continua havendo uma área
        // próxima para a sugestão de triagem, mas nenhuma o contém.
        double latitude = BEACH_MIN_Y - 0.0015;
        double longitude = BEACH_MIN_X - 0.0015;

        ResponseEntity<String> response = launch(lifeguard.accessToken(), """
                {
                  "flag": "RED",
                  "latitude": %s,
                  "longitude": %s,
                  "accuracyMeters": 25.0,
                  "notes": "Praia nova, sem placa e sem mapeamento",
                  "mediaIds": ["%s"]
                }
                """.formatted(latitude, longitude, photoId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode launched = objectMapper.readTree(response.getBody());
        assertThat(launched.get("outcome").asText()).isEqualTo("UNMAPPED");
        assertThat(launched.get("flag").isNull()).isTrue();

        JsonNode queued = launched.get("unmapped");
        assertThat(queued.get("status").asText()).isEqualTo("PENDING");
        assertThat(queued.get("flag").asText()).isEqualTo("RED");
        assertThat(queued.get("reportedByBadge").asText()).isNotBlank();
        assertThat(queued.get("latitude").asDouble()).isCloseTo(latitude, within(1e-6));
        assertThat(queued.get("photoUrls")).hasSize(1);
        // A sugestão de triagem aponta a área mapeada mais próxima, sem vincular.
        assertThat(queued.get("nearestBeachId").asText()).isEqualTo(beachId.toString());
        assertThat(queued.get("nearestBeachName").asText()).isEqualTo("Praia da Localização");
        assertThat(queued.get("nearestBeachDistanceMeters").asLong()).isPositive();
        assertThat(queued.get("beachId").isNull()).isTrue();

        UUID signalId = UUID.fromString(queued.get("id").asText());
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM unmapped_signals
                         WHERE id = ? AND status = 'PENDING'
                        """, Integer.class, signalId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM unmapped_signal_media WHERE signal_id = ?",
                Integer.class, signalId)).isEqualTo(1);

        // Nada é publicado: não existe bandeira na área nem pin no mapa.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flag_updates", Integer.class)).isZero();
        JsonNode publicFlags = objectMapper.readTree(
                restTemplate.getForEntity("/api/v1/flags", String.class).getBody());
        assertThat(publicFlags).isEmpty();
    }

    @Test
    void theCoordinationTriagesTheQueueAndCanReopenIt() throws Exception {
        importBeach();
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");
        TestOperatorAuth.Session coordinator = login("COORDINATOR");

        String first = queueUnmappedSignal(lifeguard.accessToken());
        String second = queueUnmappedSignal(lifeguard.accessToken());

        ResponseEntity<String> pending = get(coordinator.accessToken(),
                "/api/v1/management/unmapped-signals?status=PENDING");
        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode queue = objectMapper.readTree(pending.getBody());
        assertThat(queue).hasSize(2);
        assertThat(queue.get(0).get("id").asText()).isEqualTo(second);

        ResponseEntity<String> identified = decide(coordinator.accessToken(), first, """
                {
                  "decision": "IDENTIFIED",
                  "beachId": "%s",
                  "sectorId": "%s",
                  "notes": "É o setor norte da praia mapeada"
                }
                """.formatted(beachId, northSectorId));
        assertThat(identified.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode decided = objectMapper.readTree(identified.getBody());
        assertThat(decided.get("status").asText()).isEqualTo("IDENTIFIED");
        assertThat(decided.get("beachId").asText()).isEqualTo(beachId.toString());
        assertThat(decided.get("sectorId").asText()).isEqualTo(northSectorId.toString());
        assertThat(decided.get("sectorLabel").asText()).isEqualTo("Setor Norte");
        assertThat(decided.get("decidedAt").isNull()).isFalse();

        // A fila pendente perde o registro decidido; o histórico continua.
        JsonNode afterIdentify = objectMapper.readTree(get(coordinator.accessToken(),
                "/api/v1/management/unmapped-signals?status=PENDING").getBody());
        assertThat(afterIdentify).hasSize(1);
        assertThat(afterIdentify.get(0).get("id").asText()).isEqualTo(second);

        ResponseEntity<String> needsMapping = decide(coordinator.accessToken(), second, """
                {"decision": "NEEDS_MAPPING", "notes": "Praia nova: pedir ao CBM para mapear"}
                """);
        assertThat(needsMapping.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(needsMapping.getBody()).get("status").asText())
                .isEqualTo("NEEDS_MAPPING");

        ResponseEntity<String> discarded = decide(coordinator.accessToken(), second, """
                {"decision": "DISCARDED", "notes": "Duplicado de um registro anterior"}
                """);
        assertThat(discarded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(discarded.getBody()).get("status").asText())
                .isEqualTo("DISCARDED");

        // Reabrir limpa a decisão anterior e devolve o registro à fila.
        ResponseEntity<String> reopened = decide(coordinator.accessToken(), second, """
                {"decision": "PENDING"}
                """);
        assertThat(reopened.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode back = objectMapper.readTree(reopened.getBody());
        assertThat(back.get("status").asText()).isEqualTo("PENDING");
        assertThat(back.get("beachId").isNull()).isTrue();
        assertThat(back.get("decidedAt").isNull()).isTrue();

        // O histórico completo continua acessível.
        JsonNode all = objectMapper.readTree(get(coordinator.accessToken(),
                "/api/v1/management/unmapped-signals").getBody());
        assertThat(all).hasSize(2);
    }

    @Test
    void identifyingWithoutAnAreaIsRejected() throws Exception {
        importBeach();
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");
        TestOperatorAuth.Session coordinator = login("COORDINATOR");
        String signalId = queueUnmappedSignal(lifeguard.accessToken());

        ResponseEntity<String> response = decide(coordinator.accessToken(), signalId, """
                {"decision": "IDENTIFIED", "notes": "Sem área escolhida"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).get("detail").asText())
                .contains("beachId");

        // Decisão que não associa área não pode carregar uma área pela metade.
        ResponseEntity<String> inconsistent = decide(coordinator.accessToken(), signalId, """
                {"decision": "NEEDS_MAPPING", "beachId": "%s"}
                """.formatted(beachId));
        assertThat(inconsistent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theTriageQueueIsNotVisibleToTheLifeguard() throws Exception {
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");

        ResponseEntity<String> response = get(lifeguard.accessToken(),
                "/api/v1/management/unmapped-signals");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theCoordinationListsTheActiveBathingAreas() {
        importBeach();
        TestOperatorAuth.Session coordinator = login("COORDINATOR");

        ResponseEntity<String> response = get(coordinator.accessToken(),
                "/api/v1/management/bathing-areas");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode areas;
        try {
            areas = objectMapper.readTree(response.getBody());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        assertThat(areas.findValuesAsText("id")).contains(beachId.toString());
        JsonNode area = null;
        for (JsonNode candidate : areas) {
            if (candidate.get("id").asText().equals(beachId.toString())) {
                area = candidate;
                break;
            }
        }
        assertThat(area).isNotNull();
        assertThat(area.get("sectorCount").asInt()).isEqualTo(2);
    }

    @Test
    void aPhotoUploadedByAnotherOperatorCannotBeAttached() throws Exception {
        importBeach();
        TestOperatorAuth.Session owner = login("LIFEGUARD");
        TestOperatorAuth.Session other = login("LIFEGUARD");
        String photoId = objectMapper.readTree(
                        uploadPhoto(owner.accessToken(), png(30, 30)).getBody())
                .get("id").asText();

        ResponseEntity<String> response = launch(other.accessToken(), """
                {
                  "flag": "GREEN",
                  "beachId": "%s",
                  "mediaIds": ["%s"]
                }
                """.formatted(beachId, photoId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).get("detail").asText())
                .contains("outro operador");
    }

    @Test
    void aStationIsCreatedAndMovedWithTheDevicePosition() throws Exception {
        importBeach();
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");

        ResponseEntity<String> created = restTemplate.exchange(
                "/api/v1/operations/stations",
                HttpMethod.POST,
                authorized(lifeguard.accessToken(), """
                        {
                          "kind": "FIXED",
                          "name": "Posto teste %s",
                          "beachId": "%s",
                          "latitude": %s,
                          "longitude": %s,
                          "accuracyMeters": 6.0
                        }
                        """.formatted(
                        UUID.randomUUID().toString().substring(0, 8),
                        beachId,
                        BEACH_MIN_Y + 0.0005,
                        BEACH_MIN_X + 0.0005)),
                String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode station = objectMapper.readTree(created.getBody());
        UUID stationId = UUID.fromString(station.get("id").asText());
        createdStations.add(stationId);
        assertThat(station.get("kind").asText()).isEqualTo("FIXED");
        assertThat(station.get("positionSource").asText()).isEqualTo("DEVICE_GPS");
        assertThat(station.get("positionAccuracyM").asDouble()).isEqualTo(6.0);
        assertThat(station.get("positionUpdatedAt").isNull()).isFalse();
        assertThat(station.get("latitude").asDouble())
                .isCloseTo(BEACH_MIN_Y + 0.0005, within(1e-6));

        double newLatitude = BEACH_MIN_Y + 0.0008;
        double newLongitude = BEACH_MIN_X + 0.0008;
        ResponseEntity<String> moved = restTemplate.exchange(
                "/api/v1/operations/stations/FIXED/" + stationId + "/position",
                HttpMethod.PATCH,
                authorized(lifeguard.accessToken(), """
                        {"latitude": %s, "longitude": %s, "accuracyMeters": 4.0}
                        """.formatted(newLatitude, newLongitude)),
                String.class);

        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode repositioned = objectMapper.readTree(moved.getBody());
        assertThat(repositioned.get("latitude").asDouble())
                .isCloseTo(newLatitude, within(1e-6));
        assertThat(repositioned.get("longitude").asDouble())
                .isCloseTo(newLongitude, within(1e-6));
        assertThat(repositioned.get("positionSource").asText()).isEqualTo("DEVICE_GPS");

        // A estrutura reativada aparece no mapa público com a posição nova.
        ResponseEntity<String> publicStations = restTemplate.getForEntity(
                "/api/v1/stations", String.class);
        assertThat(publicStations.getBody()).contains(stationId.toString());
    }

    @Test
    void aStationIsCreatedWithPhotosForThePublicScreen() throws Exception {
        importBeach();
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");
        String firstPhoto = objectMapper.readTree(
                        uploadPhoto(lifeguard.accessToken(), png(40, 30)).getBody())
                .get("id").asText();
        String secondPhoto = objectMapper.readTree(
                        uploadPhoto(lifeguard.accessToken(), png(30, 40)).getBody())
                .get("id").asText();

        ResponseEntity<String> created = restTemplate.exchange(
                "/api/v1/operations/stations",
                HttpMethod.POST,
                authorized(lifeguard.accessToken(), """
                        {
                          "kind": "FIXED",
                          "name": "Posto com foto",
                          "beachId": "%s",
                          "latitude": %s,
                          "longitude": %s,
                          "mediaIds": ["%s", "%s"]
                        }
                        """.formatted(
                        beachId,
                        BEACH_MIN_Y + 0.0005,
                        BEACH_MIN_X + 0.0005,
                        firstPhoto,
                        secondPhoto)),
                String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode station = objectMapper.readTree(created.getBody());
        UUID stationId = UUID.fromString(station.get("id").asText());
        createdStations.add(stationId);
        assertThat(station.get("photoUrls")).hasSize(2);
        assertThat(station.get("photoUrls").get(0).asText())
                .isEqualTo("/api/v1/media/" + firstPhoto);

        // A foto do posto é pública: é o que a população vê na ficha da praia.
        JsonNode publicStations = objectMapper.readTree(
                restTemplate.getForEntity("/api/v1/stations", String.class).getBody());
        JsonNode published = null;
        for (JsonNode candidate : publicStations) {
            if (candidate.get("id").asText().equals(stationId.toString())) {
                published = candidate;
                break;
            }
        }
        assertThat(published).isNotNull();
        assertThat(published.get("photoUrls")).hasSize(2);
    }

    @Test
    void theLifeguardDeactivatesTheStationOfTheOwnShift() throws Exception {
        importBeach();
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");
        UUID stationId = createFixedPost(lifeguard.accessToken(), "Posto do plantão");

        // Abrir o plantão no posto é o que dá ao guarda-vidas o direito de
        // tirá-lo do mapa quando precisa sair ou quando ele fica indisponível.
        ResponseEntity<String> shift = restTemplate.exchange(
                "/api/v1/dispatch/shifts",
                HttpMethod.POST,
                authorized(lifeguard.accessToken(), """
                        {"postId": "%s", "latitude": %s, "longitude": %s}
                        """.formatted(
                        stationId, BEACH_MIN_Y + 0.0005, BEACH_MIN_X + 0.0005)),
                String.class);
        assertThat(shift.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> deactivated = restTemplate.exchange(
                "/api/v1/operations/stations/FIXED/" + stationId,
                HttpMethod.PATCH,
                authorized(lifeguard.accessToken(), """
                        {"active": false, "statusNote": "Posto fechado: reforma no calçadão"}
                        """),
                String.class);
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(deactivated.getBody());
        assertThat(body.get("active").asBoolean()).isFalse();
        assertThat(body.get("deactivatedAt").isNull()).isFalse();
        assertThat(objectMapper.readTree(
                        restTemplate.getForEntity("/api/v1/stations", String.class).getBody())
                .findValuesAsText("id")).doesNotContain(stationId.toString());

        ResponseEntity<String> reactivated = restTemplate.exchange(
                "/api/v1/operations/stations/FIXED/" + stationId,
                HttpMethod.PATCH,
                authorized(lifeguard.accessToken(), """
                        {"active": true}
                        """),
                String.class);
        assertThat(reactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(reactivated.getBody()).get("active").asBoolean())
                .isTrue();
        assertThat(objectMapper.readTree(
                        restTemplate.getForEntity("/api/v1/stations", String.class).getBody())
                .findValuesAsText("id")).contains(stationId.toString());
    }

    @Test
    void aLifeguardCannotToggleAStationOutsideTheOwnShift() throws Exception {
        importBeach();
        TestOperatorAuth.Session lifeguard = login("LIFEGUARD");
        UUID stationId = createFixedPost(lifeguard.accessToken(), "Posto de outro turno");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/operations/stations/FIXED/" + stationId,
                HttpMethod.PATCH,
                authorized(lifeguard.accessToken(), """
                        {"active": false}
                        """),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(objectMapper.readTree(response.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/station-outside-shift");
    }

    private UUID createFixedPost(String accessToken, String name) throws Exception {
        ResponseEntity<String> created = restTemplate.exchange(
                "/api/v1/operations/stations",
                HttpMethod.POST,
                authorized(accessToken, """
                        {
                          "kind": "FIXED",
                          "name": "%s",
                          "beachId": "%s",
                          "latitude": %s,
                          "longitude": %s
                        }
                        """.formatted(name, beachId, BEACH_MIN_Y + 0.0005, BEACH_MIN_X + 0.0005)),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID stationId = UUID.fromString(
                objectMapper.readTree(created.getBody()).get("id").asText());
        createdStations.add(stationId);
        return stationId;
    }

    private String queueUnmappedSignal(String accessToken) throws Exception {
        ResponseEntity<String> response = launch(accessToken, """
                {"flag": "YELLOW", "latitude": -20.500, "longitude": -44.000}
                """);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("outcome").asText()).isEqualTo("UNMAPPED");
        return body.get("unmapped").get("id").asText();
    }

    private void importBeach() {
        String file = kml(
                feature("1", "Praia da Localização", "Setor Norte", "Aracaju", null,
                        BEACH_MIN_X, BEACH_MIN_Y),
                feature("2", "Praia da Localização", "Setor Sul", "Aracaju", null,
                        BEACH_MIN_X + 0.010, BEACH_MIN_Y + 0.010));
        importService.importKml(new ImportCommand(
                file.getBytes(StandardCharsets.UTF_8), "localizacao.kml", "SE", false,
                ImportMode.UPSERT, null));
        beachId = jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, PREFIX + "area:1");
        northSectorId = jdbcTemplate.queryForObject(
                "SELECT id FROM beach_sectors WHERE beach_id = ? AND name = ?",
                UUID.class, beachId, "Setor Norte");
    }

    private TestOperatorAuth.Session login(String role) {
        return TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, role);
    }

    private ResponseEntity<String> launch(String accessToken, String body) {
        return restTemplate.exchange(
                "/api/v1/operations/flags",
                HttpMethod.POST,
                authorized(accessToken, body),
                String.class);
    }

    private HttpEntity<String> authorized(String accessToken, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<String> get(String accessToken, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> decide(String accessToken, String signalId, String body) {
        return restTemplate.exchange(
                "/api/v1/management/unmapped-signals/" + signalId + "/decision",
                HttpMethod.POST,
                authorized(accessToken, body),
                String.class);
    }

    private ResponseEntity<String> uploadPhoto(String accessToken, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(accessToken);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "foto.png";
            }
        });
        return restTemplate.postForEntity(
                "/api/v1/media", new HttpEntity<>(body, headers), String.class);
    }

    private static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(30, 120, 200));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.WHITE);
            graphics.fillOval(2, 2, width / 2, height / 2);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
