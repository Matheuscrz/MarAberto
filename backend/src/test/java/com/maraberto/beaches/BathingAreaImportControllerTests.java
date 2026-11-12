package com.maraberto.beaches;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.support.PostgisIntegrationTest;
import com.maraberto.support.TestOperatorAuth;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Endpoint da coordenação: multipart, `dry-run` por padrão, confirmação para
 * aplicar e RFC 9457 nos erros (ADR-016).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BathingAreaImportControllerTests extends PostgisIntegrationTest {

    private static final String PATH = "/api/v1/management/bathing-areas/import";
    private static final String PREFIX = "se-areas-banho:";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthenticationService authenticationService;

    private TestOperatorAuth.Session coordinator;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM beach_sectors WHERE external_id LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM import_batches WHERE state_code = 'SE'");
        jdbcTemplate.update("DELETE FROM beaches WHERE external_id LIKE ?", PREFIX + "%");
        coordinator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "COORDINATOR");
    }

    /**
     * A coordenação cadastrada aqui não pode virar destinatária do SOS dos
     * outros testes: o fallback do despacho manda para <em>todos</em> os
     * coordenadores ativos do banco compartilhado pela JVM.
     */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM beach_sectors WHERE external_id LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM import_batches WHERE state_code = 'SE'");
        jdbcTemplate.update("DELETE FROM beaches WHERE external_id LIKE ?", PREFIX + "%");
        jdbcTemplate.update(
                "UPDATE operational_users SET active = FALSE WHERE id = ?", coordinator.userId());
    }

    @Test
    void dryRunReturnsTheReportWithoutWriting() throws Exception {
        ResponseEntity<String> response = upload(tinyKml(), coordinator, true, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode report = objectMapper.readTree(response.getBody());
        assertThat(report.get("dryRun").asBoolean()).isTrue();
        assertThat(report.get("applied").asBoolean()).isFalse();
        assertThat(report.get("created").asInt()).isEqualTo(3);
        assertThat(report.get("state").asText()).isEqualTo("SE");
        assertThat(report.get("changes")).hasSize(3);
        assertThat(report.get("sourceVersion").asText()).hasSize(64);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM beaches WHERE external_id LIKE ?", Integer.class, PREFIX + "%"))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM import_batches WHERE state_code = 'SE'", Integer.class))
                .isZero();
    }

    @Test
    void applyingRequiresConfirmationAndPersistsTheBatch() throws Exception {
        ResponseEntity<String> refused = upload(tinyKml(), coordinator, false, false);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(refused.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/importacao-invalida");

        ResponseEntity<String> applied = upload(tinyKml(), coordinator, false, true);
        assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode report = objectMapper.readTree(applied.getBody());
        assertThat(report.get("applied").asBoolean()).isTrue();
        assertThat(report.get("created").asInt()).isEqualTo(3);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM beaches WHERE external_id LIKE ?", Integer.class, PREFIX + "%"))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM beach_sectors WHERE external_id LIKE ?",
                Integer.class, PREFIX + "%"))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT imported_by FROM import_batches WHERE state_code = 'SE'",
                java.util.UUID.class))
                .isEqualTo(coordinator.userId());
    }

    @Test
    void reimportingTheSameFileIsAnUnchangedReport() throws Exception {
        upload(tinyKml(), coordinator, false, true);
        ResponseEntity<String> again = upload(tinyKml(), coordinator, true, false);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode report = objectMapper.readTree(again.getBody());
        assertThat(report.get("created").asInt()).isZero();
        assertThat(report.get("updated").asInt()).isZero();
        assertThat(report.get("unchanged").asInt()).isEqualTo(3);
        assertThat(report.get("missing").asInt()).isZero();
    }

    @Test
    void wrongRoleIsForbiddenAndDivergentStateIsAProblemDetail() throws Exception {
        TestOperatorAuth.Session lifeguard = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        assertThat(upload(tinyKml(), lifeguard, true, false).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String otherState = """
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark>
                    <ExtendedData><SchemaData>
                      <SimpleData name="id">1</SimpleData>
                      <SimpleData name="Praia">Praia do Farol</SimpleData>
                      <SimpleData name="Municipio">Salvador</SimpleData>
                      <SimpleData name="Estado">BA</SimpleData>
                    </SchemaData></ExtendedData>
                    <Polygon><outerBoundaryIs><LinearRing><coordinates>
                      -38.5,-13.0 -38.4,-13.0 -38.4,-12.9 -38.5,-12.9 -38.5,-13.0
                    </coordinates></LinearRing></outerBoundaryIs></Polygon>
                  </Placemark>
                </kml>
                """;
        ResponseEntity<String> divergent = upload(otherState, coordinator, true, false);
        assertThat(divergent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(divergent.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode problem = objectMapper.readTree(divergent.getBody());
        assertThat(problem.get("title").asText()).isEqualTo("Importação inválida");
        assertThat(problem.get("detail").asText()).contains("BA");
    }

    private ResponseEntity<String> upload(
            String kml, TestOperatorAuth.Session session, boolean dryRun, boolean confirm)
            throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.accessToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(kml.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "areas-banho.kml";
            }
        });

        String url = PATH + "?state=SE&dryRun=" + dryRun + "&confirm=" + confirm
                + "&mode=UPSERT";
        return restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static String tinyKml() throws IOException {
        try (var input = BathingAreaImportControllerTests.class
                .getResourceAsStream("/kml/tiny-areas.kml")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
