package com.maraberto.occurrences;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.occurrences.application.OccurrenceReconciliationJob;
import com.maraberto.occurrences.infra.OccurrenceRepository;
import com.maraberto.support.PostgisIntegrationTest;
import com.maraberto.support.TestOperatorAuth;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * A ocorrência nasce no ack do SOS (ADR-013).
 *
 * <p>O guarda-vidas assume o chamado e o servidor abre a ficha do atendimento:
 * origem SOS, praia e setor resolvidos pelo ponto do banhista, ponto do
 * chamado disponível só no console autenticado. Um chamado que ninguém assume
 * não gera ocorrência.
 *
 * <p>Cada teste usa uma área de banho própria e distante das demais: o PostGIS
 * é compartilhado por toda a JVM e praias de outras classes continuam no banco.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "maraberto.emergency.outbox.sweep-initial-delay=PT1H",
                "maraberto.dispatch.max-recipients=3"
        })
class OccurrenceFromSosTests extends PostgisIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private OccurrenceRepository occurrenceRepository;

    @Autowired
    private OccurrenceReconciliationJob reconciliationJob;

    @Test
    void ackOpensTheOccurrenceWithAreaAndSectorResolvedByTheCallPoint() throws Exception {
        Area area = createArea(-12.974, -38.501, "Setor Norte");
        TestOperatorAuth.Session lifeguard = lifeguardAt(area, "LIFEGUARD");

        UUID callId = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        ack(callId, lifeguard.accessToken());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM occurrences WHERE emergency_call_id = ?", callId);
        assertThat(row.get("origin")).isEqualTo("SOS_CITIZEN");
        assertThat(row.get("nature")).isEqualTo("RESCUE");
        assertThat(row.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(row.get("ciosp_status")).isEqualTo("PENDING");
        assertThat(row.get("beach_id")).isEqualTo(area.beachId());
        assertThat(row.get("beach_sector_id")).isEqualTo(area.sectorId());
        assertThat(row.get("post_id")).isEqualTo(area.postId());
        assertThat(row.get("registered_by")).isNull();
        // O fato é a abertura do chamado; a criação é o ack.
        assertThat(((java.sql.Timestamp) row.get("occurred_at")).toInstant())
                .isBefore(((java.sql.Timestamp) row.get("created_at")).toInstant());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM occurrence_events WHERE occurrence_id = ? AND kind = 'OPENED'",
                Integer.class, row.get("id"))).isEqualTo(1);

        JsonNode[] listed = list(lifeguard.accessToken(), 50);
        assertThat(listed).hasSize(1);
        JsonNode occurrence = listed[0];
        assertThat(occurrence.get("emergencyCallId").asText()).isEqualTo(callId.toString());
        assertThat(occurrence.get("number").asLong()).isPositive();
        assertThat(occurrence.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(occurrence.get("ciospStatus").asText()).isEqualTo("PENDING");
        assertThat(occurrence.get("beachId").asText()).isEqualTo(area.beachId().toString());
        assertThat(occurrence.get("beachName").asText()).isEqualTo(area.beachName());
        assertThat(occurrence.get("sectorLabel").asText()).isEqualTo("Setor Norte");
        assertThat(occurrence.get("postName").asText()).isEqualTo(area.postName());
        assertThat(occurrence.get("latitude").asDouble()).isEqualTo(area.centerLatitude());
        assertThat(occurrence.get("longitude").asDouble()).isEqualTo(area.centerLongitude());
        assertThat(occurrence.get("acknowledgedAt").isNull()).isFalse();
        assertThat(occurrence.get("closedAt").isNull()).isTrue();

        ResponseEntity<String> ficha = get(
                occurrence.get("id").asText(), lifeguard.accessToken());
        assertThat(ficha.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(ficha.getBody())
                .get("occurrence").get("number").asLong())
                .isEqualTo(occurrence.get("number").asLong());
    }

    @Test
    void coordinationSeesEveryOccurrenceAndAnotherGuardDoesNotSeeThisOne() throws Exception {
        Area area = createArea(-12.774, -38.501, null);
        TestOperatorAuth.Session owner = lifeguardAt(area, "LIFEGUARD");
        UUID callId = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        ack(callId, owner.accessToken());

        TestOperatorAuth.Session coordinator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "COORDINATOR");
        JsonNode[] all = list(coordinator.accessToken(), 50);
        assertThat(all).hasSize(1);
        // Setor sem nome é identificado pela ordem do mapeamento.
        assertThat(all[0].get("sectorLabel").asText()).isEqualTo("Setor 1");

        // Guarda-vidas de outra área, sem plantão no local e sem despacho: a
        // ocorrência não existe para ele — nem na lista, nem pela ficha.
        TestOperatorAuth.Session outsider = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        assertThat(list(outsider.accessToken(), 50)).isEmpty();
        ResponseEntity<String> hidden =
                get(all[0].get("id").asText(), outsider.accessToken());
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden.getHeaders().getContentType()).isNotNull()
                .satisfies(type -> assertThat(
                        type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
        assertThat(objectMapper.readTree(hidden.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/occurrence-not-found");
    }

    @Test
    void acknowledgedCallOutsideTheMappedAreaStillOpensAnOccurrenceWithThePoint() throws Exception {
        Area area = createArea(-12.374, -38.501, null);
        // O posto fica na área mapeada, mas o chamado é 10 km ao norte dela.
        double callLatitude = area.centerLatitude() + 0.094;
        TestOperatorAuth.Session lifeguard = lifeguardAt(
                area, "LIFEGUARD", callLatitude);

        UUID callId = createEmergencyCall(callLatitude, area.centerLongitude());
        ack(callId, lifeguard.accessToken());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT beach_id, beach_sector_id, location FROM occurrences "
                        + "WHERE emergency_call_id = ?", callId);
        assertThat(row.get("beach_id")).isNull();
        assertThat(row.get("beach_sector_id")).isNull();
        assertThat(row.get("location")).isNotNull();
    }

    @Test
    void openingIsIdempotentAndReconciliationFillsTheGap() throws Exception {
        Area area = createArea(-12.174, -38.501, null);
        TestOperatorAuth.Session lifeguard = lifeguardAt(area, "LIFEGUARD");
        UUID callId = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        ack(callId, lifeguard.accessToken());

        // Evento entregue de novo (at-least-once): nada duplica.
        assertThat(occurrenceRepository.openFromAcknowledgedCall(callId)).isZero();
        assertThat(occurrenceCount(callId)).isEqualTo(1);

        // Listener falhou: o job abre a ocorrência do chamado assumido há mais
        // de um minuto, sem duplicar o que já existe.
        jdbcTemplate.update("DELETE FROM occurrence_events");
        jdbcTemplate.update("DELETE FROM occurrences");
        jdbcTemplate.update("""
                UPDATE emergency_calls
                SET acknowledged_at = acknowledged_at - INTERVAL '5 minutes'
                WHERE id = ?
                """, callId);

        reconciliationJob.reconcile();

        assertThat(occurrenceCount(callId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM occurrences WHERE emergency_call_id = ?",
                String.class, callId)).isEqualTo("IN_PROGRESS");

        reconciliationJob.reconcile();
        assertThat(occurrenceCount(callId)).isEqualTo(1);
    }

    @Test
    void closingTheOccurrenceEndsTheCallAndMovesItToTheClosedList() throws Exception {
        Area area = createArea(-11.974, -38.501, "Setor Sul");
        TestOperatorAuth.Session lifeguard = lifeguardAt(area, "LIFEGUARD");
        UUID callId = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        ack(callId, lifeguard.accessToken());

        String occurrenceId = list(lifeguard.accessToken(), 50)[0].get("id").asText();
        ResponseEntity<String> closed = post(
                "/api/v1/occurrences/" + occurrenceId + "/close",
                """
                {
                  "outcome": "RESOLVED",
                  "nature": "FIRST_AID",
                  "summary": "Banhista com cãibra na perna direita, retirado da água.",
                  "outcomeNotes": "Orientado a não voltar à água hoje.",
                  "ciospProtocol": "CIOSP-2026-001",
                  "victimName": "Maria da Silva",
                  "victimAge": "34",
                  "victimContact": "(79) 99999-0000",
                  "victimCondition": "Cãibra, consciente e orientada."
                }
                """,
                lifeguard.accessToken());
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode detail = objectMapper.readTree(closed.getBody());
        JsonNode occurrence = detail.get("occurrence");
        assertThat(occurrence.get("status").asText()).isEqualTo("CLOSED");
        assertThat(occurrence.get("outcome").asText()).isEqualTo("RESOLVED");
        assertThat(occurrence.get("closedAt").isNull()).isFalse();
        assertThat(occurrence.get("ciospStatus").asText()).isEqualTo("REGISTERED");
        assertThat(occurrence.get("responderId").asText())
                .isEqualTo(lifeguard.userId().toString());
        assertThat(occurrence.get("responderName").asText()).isNotBlank();
        assertThat(detail.get("summary").asText()).contains("cãibra");
        assertThat(detail.get("ciospProtocol").asText()).isEqualTo("CIOSP-2026-001");
        assertThat(detail.get("victim").get("recorded").asBoolean()).isTrue();
        assertThat(detail.get("victim").get("revealed").asBoolean()).isFalse();
        assertThat(detail.get("victim").get("name").isNull()).isTrue();

        // O chamado sai de "Em atendimento por você" e fica encerrado no banco.
        assertThat(objectMapper.readValue(restTemplate.exchange(
                        "/api/v1/dispatch/calls/assigned",
                        HttpMethod.GET,
                        new HttpEntity<>(authorization(lifeguard.accessToken())),
                        String.class).getBody(), JsonNode[].class)).isEmpty();
        Map<String, Object> call = jdbcTemplate.queryForMap(
                "SELECT status, resolved_at, resolved_by FROM emergency_calls WHERE id = ?",
                callId);
        assertThat(call.get("status")).isEqualTo("RESOLVED");
        assertThat(call.get("resolved_at")).isNotNull();
        assertThat(call.get("resolved_by")).isEqualTo(lifeguard.userId());

        // A lista em atendimento esvazia; a de encerradas recebe a ficha.
        assertThat(listByStatus(lifeguard.accessToken(), "IN_PROGRESS")).isEmpty();
        JsonNode[] closedList = listByStatus(lifeguard.accessToken(), "CLOSED");
        assertThat(closedList).hasSize(1);
        assertThat(closedList[0].get("outcome").asText()).isEqualTo("RESOLVED");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM occurrence_events "
                        + "WHERE occurrence_id = ? AND kind = 'STATUS_CHANGED'",
                Integer.class, UUID.fromString(occurrenceId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM occurrence_events "
                        + "WHERE occurrence_id = ? AND kind = 'CIOSP_REGISTERED'",
                Integer.class, UUID.fromString(occurrenceId))).isEqualTo(1);

        // Revelar a vítima é explícito, com motivo, e fica auditado.
        ResponseEntity<String> reveal = post(
                "/api/v1/occurrences/" + occurrenceId + "/victim/reveal",
                "{\"reason\": \"Preenchimento da ficha do CIOSP\"}",
                lifeguard.accessToken());
        assertThat(reveal.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode victim = objectMapper.readTree(reveal.getBody());
        assertThat(victim.get("revealed").asBoolean()).isTrue();
        assertThat(victim.get("name").asText()).isEqualTo("Maria da Silva");
        assertThat(victim.get("condition").asText()).contains("consciente");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT detail ->> 'reason' FROM occurrence_events "
                        + "WHERE occurrence_id = ? AND kind = 'PERSON_REVEALED'",
                String.class, UUID.fromString(occurrenceId)))
                .isEqualTo("Preenchimento da ficha do CIOSP");
        // O histórico não guarda o dado da vítima em claro.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM occurrence_events "
                        + "WHERE occurrence_id = ? AND detail::text LIKE '%Maria%'",
                Integer.class, UUID.fromString(occurrenceId))).isZero();
    }

    @Test
    void coordinationCompletesCiospAndVictimAfterTheClosure() throws Exception {
        Area area = createArea(-11.774, -38.501, null);
        TestOperatorAuth.Session lifeguard = lifeguardAt(area, "LIFEGUARD");
        TestOperatorAuth.Session coordinator = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "COORDINATOR");
        UUID callId = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        ack(callId, lifeguard.accessToken());

        String occurrenceId = list(lifeguard.accessToken(), 50)[0].get("id").asText();
        assertThat(post(
                "/api/v1/occurrences/" + occurrenceId + "/close",
                """
                {"outcome": "REFERRED", "summary": "Encaminhado ao hospital da capital."}
                """,
                lifeguard.accessToken()).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Sem protocolo, o CIOSP continua pendente mesmo encerrando o atendimento.
        JsonNode pending = getDetail(occurrenceId, coordinator.accessToken());
        assertThat(pending.get("occurrence").get("status").asText()).isEqualTo("CLOSED");
        assertThat(pending.get("occurrence").get("outcome").asText()).isEqualTo("REFERRED");
        assertThat(pending.get("occurrence").get("ciospStatus").asText()).isEqualTo("PENDING");
        assertThat(pending.get("victim").get("recorded").asBoolean()).isFalse();

        // A coordenação completa o protocolo e os dados que só apareceram depois.
        ResponseEntity<String> updated = patch(
                "/api/v1/occurrences/" + occurrenceId,
                """
                {
                  "ciospProtocol": "CIOSP-2026-777",
                  "victimName": "João Pereira",
                  "victimAge": "52",
                  "victimCondition": "Imobilizado, encaminhado consciente."
                }
                """,
                coordinator.accessToken());
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode detail = objectMapper.readTree(updated.getBody());
        assertThat(detail.get("occurrence").get("ciospStatus").asText())
                .isEqualTo("REGISTERED");
        assertThat(detail.get("ciospProtocol").asText()).isEqualTo("CIOSP-2026-777");
        assertThat(detail.get("ciospRegisteredAt").isNull()).isFalse();
        assertThat(detail.get("victim").get("recorded").asBoolean()).isTrue();
        assertThat(detail.get("victim").get("revealed").asBoolean()).isFalse();

        // Registrar sem protocolo é recusado com 409 e tipo estável.
        Area other = createArea(-11.574, -38.501, null);
        TestOperatorAuth.Session second = lifeguardAt(other, "LIFEGUARD");
        UUID secondCall = createEmergencyCall(
                other.centerLatitude(), other.centerLongitude());
        ack(secondCall, second.accessToken());
        String secondOccurrence = list(second.accessToken(), 50)[0].get("id").asText();
        ResponseEntity<String> invalid = patch(
                "/api/v1/occurrences/" + secondOccurrence,
                "{\"ciospStatus\": \"REGISTERED\"}",
                coordinator.accessToken());
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(invalid.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/ciosp-protocol-required");
    }

    @Test
    void anotherGuardCannotCloseEditOrRevealWhatHeDidNotAttend() throws Exception {
        Area area = createArea(-11.374, -38.501, null);
        TestOperatorAuth.Session owner = lifeguardAt(area, "LIFEGUARD");
        TestOperatorAuth.Session outsider = TestOperatorAuth.registerAndLogin(
                authenticationService, restTemplate, objectMapper, "LIFEGUARD");
        UUID callId = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        ack(callId, owner.accessToken());

        String occurrenceId = list(owner.accessToken(), 50)[0].get("id").asText();
        assertThat(post(
                "/api/v1/occurrences/" + occurrenceId + "/close",
                """
                {"outcome": "RESOLVED", "summary": "Atendimento concluído."}
                """,
                outsider.accessToken()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(patch(
                "/api/v1/occurrences/" + occurrenceId,
                "{\"summary\": \"Tentativa de alteração\"}",
                outsider.accessToken()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(
                "/api/v1/occurrences/" + occurrenceId + "/victim/reveal",
                "{\"reason\": \"curiosidade\"}",
                outsider.accessToken()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getDetail(occurrenceId, outsider.accessToken())).isNull();
    }

    @Test
    void falseAlarmClosesTheCallWithoutCiosp() throws Exception {
        Area area = createArea(-11.174, -38.501, null);
        TestOperatorAuth.Session lifeguard = lifeguardAt(area, "LIFEGUARD");
        UUID callId = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        ack(callId, lifeguard.accessToken());

        String occurrenceId = list(lifeguard.accessToken(), 50)[0].get("id").asText();
        ResponseEntity<String> response = post(
                "/api/v1/occurrences/" + occurrenceId + "/close",
                """
                {"outcome": "FALSE_ALARM", "summary": "Nada encontrado no ponto informado."}
                """,
                lifeguard.accessToken());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode occurrence = objectMapper.readTree(response.getBody()).get("occurrence");
        assertThat(occurrence.get("status").asText()).isEqualTo("FALSE_ALARM");
        assertThat(occurrence.get("ciospStatus").asText()).isEqualTo("NOT_REQUIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM emergency_calls WHERE id = ?", String.class, callId))
                .isEqualTo("FALSE_ALARM");

        // A ficha continua existindo para consulta e edição: nada é apagado.
        assertThat(listByStatus(lifeguard.accessToken(), "CLOSED")).isEmpty();
        assertThat(listByStatus(lifeguard.accessToken(), "FALSE_ALARM")).hasSize(1);

        ResponseEntity<String> again = post(
                "/api/v1/occurrences/" + occurrenceId + "/close",
                """
                {"outcome": "RESOLVED", "summary": "Tentativa de encerrar de novo."}
                """,
                lifeguard.accessToken());
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objectMapper.readTree(again.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/occurrence-already-closed");
    }

    /**
     * O mapa de situação da coordenação só quer o que ainda pede ação: ficha
     * aberta/em atendimento ou pendência de transcrição no CIOSP. A ficha
     * encerrada sem protocolo continua no filtro; a registrada sai.
     */
    @Test
    void activeFilterKeepsOnlyOccurrencesThatStillNeedAction() throws Exception {
        Area area = createArea(-10.574, -38.501, null);
        TestOperatorAuth.Session lifeguard = lifeguardAt(area, "LIFEGUARD");

        UUID registeredCall = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        ack(registeredCall, lifeguard.accessToken());
        UUID pendingCall = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        ack(pendingCall, lifeguard.accessToken());

        assertThat(listActive(lifeguard.accessToken())).hasSize(2);

        String registeredId = occurrenceIdOf(registeredCall);
        String pendingId = occurrenceIdOf(pendingCall);

        // Encerrada com o protocolo do CIOSP: não pede mais ação.
        assertThat(post(
                "/api/v1/occurrences/" + registeredId + "/close",
                """
                {
                  "outcome": "RESOLVED",
                  "ciospProtocol": "CIOSP-2026-777",
                  "summary": "Banhista retirado da água sem ferimentos."
                }
                """,
                lifeguard.accessToken()).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode[] active = listActive(lifeguard.accessToken());
        assertThat(active).hasSize(1);
        assertThat(active[0].get("id").asText()).isEqualTo(pendingId);

        // Encerrar não basta: sem protocolo, a pendência do CIOSP segue pedindo ação.
        assertThat(post(
                "/api/v1/occurrences/" + pendingId + "/close",
                """
                {"outcome": "RESOLVED", "summary": "Atendimento concluído na areia."}
                """,
                lifeguard.accessToken()).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode[] stillPending = listActive(lifeguard.accessToken());
        assertThat(stillPending).hasSize(1);
        assertThat(stillPending[0].get("ciospStatus").asText()).isEqualTo("PENDING");

        // O filtro é só do que pede ação: o histórico completo continua igual.
        assertThat(list(lifeguard.accessToken(), 50)).hasSize(2);
    }

    @Test
    void cursorPaginationDoesNotRepeatOrSkipWhenAnOccurrenceClosesBetweenPages() throws Exception {
        Area area = createArea(-10.974, -38.501, null);
        TestOperatorAuth.Session lifeguard = lifeguardAt(area, "LIFEGUARD");
        UUID oldestCall = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        UUID middleCall = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        UUID newestCall = createEmergencyCall(area.centerLatitude(), area.centerLongitude());
        ack(oldestCall, lifeguard.accessToken());
        ack(middleCall, lifeguard.accessToken());
        ack(newestCall, lifeguard.accessToken());

        setOccurredAt(oldestCall, 3);
        setOccurredAt(middleCall, 2);
        setOccurredAt(newestCall, 1);

        JsonNode firstPage = getPage(lifeguard.accessToken(), "limit=2");
        JsonNode[] head = items(firstPage);
        assertThat(head).hasSize(2);
        assertThat(head[0].get("number").asLong())
                .isGreaterThan(head[1].get("number").asLong());
        assertThat(firstPage.get("hasMore").asBoolean()).isTrue();
        JsonNode cursor = firstPage.get("nextCursor");
        assertThat(cursor.isNull()).isFalse();

        // Encerrar uma ficha entre as páginas muda só o status; a chave
        // (occurred_at, number) continua a mesma.
        String closedId = head[0].get("id").asText();
        assertThat(post(
                "/api/v1/occurrences/" + closedId + "/close",
                """
                {"outcome": "RESOLVED", "summary": "Encerrada entre as páginas."}
                """,
                lifeguard.accessToken()).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode secondPage = getPage(lifeguard.accessToken(),
                "limit=2&beforeOccurredAt=" + cursor.get("occurredAt").asText()
                        + "&beforeNumber=" + cursor.get("number").asLong());
        JsonNode[] tail = items(secondPage);
        assertThat(tail).hasSize(1);
        assertThat(secondPage.get("hasMore").asBoolean()).isFalse();
        assertThat(secondPage.get("nextCursor").isNull()).isTrue();

        List<String> seen = new ArrayList<>();
        for (JsonNode node : head) {
            seen.add(node.get("id").asText());
        }
        for (JsonNode node : tail) {
            seen.add(node.get("id").asText());
        }
        assertThat(seen)
                .as("o keyset não repete nem pula a ficha encerrada entre as páginas")
                .doesNotHaveDuplicates()
                .hasSize(3);
        assertThat(tail[0].get("id").asText())
                .isNotEqualTo(head[0].get("id").asText())
                .isNotEqualTo(head[1].get("id").asText());
        assertThat(tail[0].get("status").asText()).isEqualTo("IN_PROGRESS");

        // Cursor incompleto é erro de requisição com tipo estável (RFC 9457).
        ResponseEntity<String> invalid = restTemplate.exchange(
                "/api/v1/occurrences?beforeNumber=1",
                HttpMethod.GET,
                new HttpEntity<>(authorization(lifeguard.accessToken())),
                String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(invalid.getBody()).get("type").asText())
                .isEqualTo("https://maraberto.org/problems/invalid-request");
    }

    private JsonNode[] listByStatus(String accessToken, String status) throws Exception {
        return items(getPage(accessToken, "status=" + status));
    }

    /** Fichas que ainda pedem ação (`active=true`), como no mapa de situação. */
    private JsonNode[] listActive(String accessToken) throws Exception {
        return items(getPage(accessToken, "active=true"));
    }

    private String occurrenceIdOf(UUID callId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM occurrences WHERE emergency_call_id = ?",
                UUID.class, callId).toString();
    }

    private JsonNode getDetail(String occurrenceId, String accessToken) throws Exception {
        ResponseEntity<String> response = get(occurrenceId, accessToken);
        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            return null;
        }
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> post(String path, String body, String accessToken) {
        return restTemplate.exchange(
                path,
                HttpMethod.POST,
                jsonEntity(body, accessToken),
                String.class);
    }

    private ResponseEntity<String> patch(String path, String body, String accessToken) {
        return restTemplate.exchange(
                path,
                HttpMethod.PATCH,
                jsonEntity(body, accessToken),
                String.class);
    }

    private static HttpEntity<String> jsonEntity(String body, String accessToken) {
        HttpHeaders headers = authorization(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private int occurrenceCount(UUID callId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM occurrences WHERE emergency_call_id = ?",
                Integer.class, callId);
    }

    private JsonNode[] list(String accessToken, int limit) throws Exception {
        return items(getPage(accessToken, "limit=" + limit));
    }

    private JsonNode getPage(String accessToken, String query) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/occurrences?" + query,
                HttpMethod.GET,
                new HttpEntity<>(authorization(accessToken)),
                String.class);
        assertThat(response.getStatusCode())
                .withFailMessage("GET /api/v1/occurrences?%s respondeu %s: %s",
                        query, response.getStatusCode(), response.getBody())
                .isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    /**
     * A lista vem em {@code items} desde a paginação por cursor; os testes
     * continuam comparando o array de fichas.
     */
    private static JsonNode[] items(JsonNode page) {
        JsonNode items = page.get("items");
        JsonNode[] result = new JsonNode[items.size()];
        for (int index = 0; index < items.size(); index++) {
            result[index] = items.get(index);
        }
        return result;
    }

    /**
     * Congela o horário do fato para a ordenação ser determinística. O
     * {@code occurred_at} nasce do chamado e não muda quando a ficha é
     * encerrada — é justamente ele que sustenta o keyset.
     */
    private void setOccurredAt(UUID callId, int minutesAgo) {
        jdbcTemplate.update(
                "UPDATE occurrences SET occurred_at = ? WHERE emergency_call_id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutesAgo),
                callId);
    }

    private ResponseEntity<String> get(String occurrenceId, String accessToken) {
        return restTemplate.exchange(
                "/api/v1/occurrences/" + occurrenceId,
                HttpMethod.GET,
                new HttpEntity<>(authorization(accessToken)),
                String.class);
    }

    private static HttpHeaders authorization(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private void ack(UUID callId, String accessToken) {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/dispatch/calls/" + callId + "/ack",
                HttpMethod.POST,
                new HttpEntity<>(authorization(accessToken)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
        return lifeguardAt(area, role, area.centerLatitude());
    }

    private TestOperatorAuth.Session lifeguardAt(
            Area area, String role, double shiftLatitude) {
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
                area.centerLongitude(), shiftLatitude);
        return session;
    }

    /**
     * Área de banho de teste com um setor e um posto dentro dela. Os polígonos
     * são quadrados em volta do centro, longe das áreas de outras classes.
     */
    private Area createArea(double centerLatitude, double centerLongitude, String sectorName) {
        UUID beachId = UUID.randomUUID();
        String beachName = "Área de ocorrência " + beachId.toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO beaches (id, name, state_code, boundary)
                VALUES (?, ?, 'SE', ST_Multi(ST_GeomFromText(?, 4326)))
                """, beachId, beachName, square(centerLatitude, centerLongitude, 0.05, 0.1));

        UUID sectorId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO beach_sectors
                    (id, beach_id, external_id, name, display_order, geometry, geometry_hash)
                VALUES (?, ?, ?, ?, 1, ST_Multi(ST_GeomFromText(?, 4326)), ?)
                """, sectorId, beachId, "SE-teste:" + sectorId, sectorName,
                square(centerLatitude, centerLongitude, 0.02, 0.05), "hash-" + sectorId);

        UUID postId = UUID.randomUUID();
        String postName = "Posto de ocorrência " + postId.toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO lifeguard_posts
                    (id, beach_id, name, location, active, coverage_radius_m)
                VALUES (?, ?, ?, CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography),
                        TRUE, 50000)
                """, postId, beachId, postName, centerLongitude, centerLatitude);

        return new Area(beachId, beachName, sectorId, postId, postName,
                centerLatitude, centerLongitude);
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
            String beachName,
            UUID sectorId,
            UUID postId,
            String postName,
            double centerLatitude,
            double centerLongitude) {
    }
}
