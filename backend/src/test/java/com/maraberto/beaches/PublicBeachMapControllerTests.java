package com.maraberto.beaches;

import static com.maraberto.support.KmlFixtures.feature;
import static com.maraberto.support.KmlFixtures.kml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.beaches.application.BathingAreaImportService;
import com.maraberto.beaches.application.BathingAreaImportService.ImportCommand;
import com.maraberto.beaches.application.ImportMode;
import com.maraberto.support.PostgisIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Endpoint público da geometria do mapa: corpo GeoJSON e contrato de cache.
 *
 * <p>O {@code ETag} é por área e por versão do mapa: repetir o request com
 * {@code If-None-Match} responde {@code 304} sem corpo, e o mesmo cabeçalho
 * nunca vale para outra praia.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicBeachMapControllerTests extends PostgisIntegrationTest {

    private static final String PREFIX = "se-areas-banho:";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BathingAreaImportService importService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanImportedMap() {
        jdbcTemplate.update("DELETE FROM beach_sectors WHERE external_id LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM import_batches WHERE state_code = 'SE'");
        jdbcTemplate.update("DELETE FROM beaches WHERE external_id LIKE ?", PREFIX + "%");
    }

    @Test
    void mapReturnsTheGeometryAndAnswersNotModifiedForTheSameEtag() throws Exception {
        UUID beachId = importBeach();

        MvcResult first = mockMvc.perform(get("/api/v1/beaches/{id}/map", beachId))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=60, public"))
                .andReturn();

        JsonNode body = objectMapper.readTree(
                first.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("id").asText()).isEqualTo(beachId.toString());
        // O PostGIS emite "Polygon" quando o MultiPolygon tem uma única parte.
        assertThat(body.get("geometry").get("type").asText()).isIn("Polygon", "MultiPolygon");
        assertThat(body.get("bbox").get("minLon").asDouble()).isLessThan(
                body.get("bbox").get("maxLon").asDouble());
        assertThat(body.get("sectors")).hasSize(2);
        assertThat(body.get("sectors").get(0).get("bbox").get("minLat").asDouble()).isNotZero();

        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(get("/api/v1/beaches/{id}/map", beachId)
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    void theEtagOfOneAreaDoesNotAnswerForAnother() throws Exception {
        importBeachWithTwoAreas();
        UUID first = beachId(PREFIX + "area:1");
        UUID second = beachId(PREFIX + "area:2");

        MvcResult response = mockMvc.perform(get("/api/v1/beaches/{id}/map", first))
                .andReturn();
        String etag = response.getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/v1/beaches/{id}/map", second)
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isOk());
    }

    @Test
    void unknownAreaIsAProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/beaches/{id}/map", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("praia-nao-encontrada"));
    }

    private UUID importBeach() {
        String file = kml(
                feature("1", "Praia Perto", "Setor Norte", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Perto", "Setor Sul", "Aracaju", null, -35.020, -10.020));
        importService.importKml(new ImportCommand(
                file.getBytes(StandardCharsets.UTF_8), "areas.kml", "SE", false,
                ImportMode.UPSERT, null));
        return beachId(PREFIX + "area:1");
    }

    private void importBeachWithTwoAreas() {
        String file = kml(
                feature("1", "Praia Perto", "Setor Único", "Aracaju", null, -35.010, -10.010),
                feature("2", "Praia Nova", "Setor Único", "Aracaju", null, -35.020, -10.020));
        importService.importKml(new ImportCommand(
                file.getBytes(StandardCharsets.UTF_8), "areas.kml", "SE", false,
                ImportMode.UPSERT, null));
    }

    private UUID beachId(String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM beaches WHERE external_id = ?", UUID.class, externalId);
    }
}
