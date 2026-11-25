package com.maraberto.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.support.PostgisIntegrationTest;
import com.maraberto.support.TestOperatorAuth;
import java.util.List;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContentIntegrationTests extends PostgisIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthenticationService authenticationService;

    private TestOperatorAuth.Session lifeguard;
    private TestOperatorAuth.Session coordinator;

    @BeforeEach
    void setUp() {
        clean();
        lifeguard = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        coordinator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "COORDINATOR");
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        jdbcTemplate.execute("TRUNCATE TABLE educational_content");
        jdbcTemplate.execute("DELETE FROM auth_sessions");
        jdbcTemplate.execute("DELETE FROM operational_users");
    }

    @Test
    void coordinatorCanCreateEditReorderUnpublishAndDeleteAndPublicReflectsImmediately()
            throws Exception {
        // Warm-up do cache público com lista vazia.
        ResponseEntity<String> initialPublic = restTemplate.getForEntity(
                "/api/v1/content", String.class);
        assertThat(initialPublic.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(initialPublic.getBody())).isEmpty();

        // LIFEGUARD não tem permissão no CMS.
        ResponseEntity<String> forbidden = restTemplate.exchange(
                "/api/v1/management/content",
                HttpMethod.POST,
                jsonEntity(lifeguard.accessToken(), Map.of(
                        "kind", "GUIDE",
                        "title", "Guia restrito",
                        "description", "Texto",
                        "category", "Geral")),
                String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Cria vídeo publicado.
        ResponseEntity<String> createdVideo = restTemplate.exchange(
                "/api/v1/management/content",
                HttpMethod.POST,
                jsonEntity(coordinator.accessToken(), Map.of(
                        "kind", "VIDEO",
                        "title", "Como identificar valas",
                        "description", "Observe faixas escuras sem ondas quebrando.",
                        "category", "Corrente de Retorno",
                        "tags", List.of("valas", "prevenção"),
                        "youtubeId", "dQw4w9WgXcQ",
                        "duration", "03:15",
                        "published", true)),
                String.class);
        assertThat(createdVideo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID videoId = UUID.fromString(objectMapper.readTree(createdVideo.getBody()).get("id").asText());

        // Leitura pública reflete imediatamente (cache invalidado).
        JsonNode publicAfterVideo = objectMapper.readTree(
                restTemplate.getForEntity("/api/v1/content", String.class).getBody());
        assertThat(publicAfterVideo).hasSize(1);
        assertThat(publicAfterVideo.get(0).get("youtubeId").asText()).isEqualTo("dQw4w9WgXcQ");

        // Cria material de divulgação como rascunho.
        ResponseEntity<String> createdCampaign = restTemplate.exchange(
                "/api/v1/management/content",
                HttpMethod.POST,
                jsonEntity(coordinator.accessToken(), Map.of(
                        "kind", "CAMPAIGN",
                        "title", "Cartilha Verão Seguro",
                        "description", "Material oficial de orientação aos banhistas.",
                        "category", "Divulgação",
                        "externalUrl", "https://maraberto.org/cartilha",
                        "externalLinkLabel", "Abrir cartilha",
                        "published", false)),
                String.class);
        assertThat(createdCampaign.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID campaignId = UUID.fromString(objectMapper.readTree(createdCampaign.getBody()).get("id").asText());

        // Rascunho não aparece na leitura pública, mas aparece na gestão.
        assertThat(objectMapper.readTree(
                restTemplate.getForEntity("/api/v1/content", String.class).getBody())).hasSize(1);
        ResponseEntity<String> mgmtList = restTemplate.exchange(
                "/api/v1/management/content",
                HttpMethod.GET,
                authEntity(coordinator.accessToken()),
                String.class);
        assertThat(objectMapper.readTree(mgmtList.getBody())).hasSize(2);

        // Publica o material de divulgação.
        ResponseEntity<String> published = restTemplate.exchange(
                "/api/v1/management/content/" + campaignId + "/published",
                HttpMethod.PATCH,
                jsonEntity(coordinator.accessToken(), Map.of("published", true)),
                String.class);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(
                restTemplate.getForEntity("/api/v1/content", String.class).getBody())).hasSize(2);

        // Reordena colocando a campanha em primeiro.
        ResponseEntity<String> reordered = restTemplate.exchange(
                "/api/v1/management/content/reorder",
                HttpMethod.PUT,
                jsonEntity(coordinator.accessToken(), Map.of("orderedIds", List.of(campaignId, videoId))),
                String.class);
        assertThat(reordered.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode publicOrdered = objectMapper.readTree(
                restTemplate.getForEntity("/api/v1/content", String.class).getBody());
        assertThat(publicOrdered.get(0).get("id").asText()).isEqualTo(campaignId.toString());
        assertThat(publicOrdered.get(1).get("id").asText()).isEqualTo(videoId.toString());

        // Atualiza título e despublica o vídeo.
        ResponseEntity<String> updated = restTemplate.exchange(
                "/api/v1/management/content/" + videoId,
                HttpMethod.PUT,
                jsonEntity(coordinator.accessToken(), Map.of(
                        "kind", "VIDEO",
                        "title", "Como identificar valas (atualizado)",
                        "description", "Observe faixas escuras sem ondas quebrando.",
                        "category", "Corrente de Retorno",
                        "youtubeId", "dQw4w9WgXcQ",
                        "published", false)),
                String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(
                restTemplate.getForEntity("/api/v1/content", String.class).getBody())).hasSize(1);

        // Exclui o vídeo.
        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/v1/management/content/" + videoId,
                HttpMethod.DELETE,
                authEntity(coordinator.accessToken()),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void rejectsMalformedUrlAndInvalidYoutubeIdWithRfc9457() throws Exception {
        ResponseEntity<String> badYoutube = restTemplate.exchange(
                "/api/v1/management/content",
                HttpMethod.POST,
                jsonEntity(coordinator.accessToken(), Map.of(
                        "kind", "VIDEO",
                        "title", "Vídeo inválido",
                        "description", "Teste",
                        "category", "Geral",
                        "youtubeId", "https://youtube.com/watch?v=123",
                        "published", true)),
                String.class);
        assertThat(badYoutube.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badYoutube.getHeaders().getContentType()).isNotNull();
        assertThat(badYoutube.getHeaders().getContentType().toString())
                .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode youtubeProblem = objectMapper.readTree(badYoutube.getBody());
        assertThat(youtubeProblem.get("status").asInt()).isEqualTo(400);

        ResponseEntity<String> badUrl = restTemplate.exchange(
                "/api/v1/management/content",
                HttpMethod.POST,
                jsonEntity(coordinator.accessToken(), Map.of(
                        "kind", "CAMPAIGN",
                        "title", "Link inválido",
                        "description", "Teste",
                        "category", "Geral",
                        "externalUrl", "javascript:alert(1)",
                        "published", true)),
                String.class);
        assertThat(badUrl.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badUrl.getHeaders().getContentType()).isNotNull();
        assertThat(badUrl.getHeaders().getContentType().toString())
                .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode urlProblem = objectMapper.readTree(badUrl.getBody());
        assertThat(urlProblem.get("status").asInt()).isEqualTo(400);
    }

    private static HttpEntity<Void> authEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<Map<String, Object>> jsonEntity(
            String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
