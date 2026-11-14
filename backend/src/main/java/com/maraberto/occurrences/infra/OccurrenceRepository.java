package com.maraberto.occurrences.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.occurrences.api.OccurrenceView;
import com.maraberto.occurrences.domain.CiospStatus;
import com.maraberto.occurrences.domain.OccurrenceNature;
import com.maraberto.occurrences.domain.OccurrenceOrigin;
import com.maraberto.occurrences.domain.OccurrenceOutcome;
import com.maraberto.occurrences.domain.OccurrenceStatus;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persistência da ocorrência e leitura do console.
 *
 * <p>A abertura a partir do SOS é um {@code INSERT … SELECT} sobre o chamado
 * assumido: o evento só traz o identificador, e o ponto do banhista nunca sai
 * da linha do chamado. A praia e o setor são resolvidos pelo PostGIS no mesmo
 * comando (setor vence a área, como na amostra de ocupação), então uma
 * ocorrência fora do mapeamento continua válida — com coordenada e sem praia.
 *
 * <p>A visibilidade do guarda-vidas é "o que eu atendi" ({@code responder_id}),
 * com fallback pelo despacho assumido para fichas antigas. Coordenação e TI
 * veem tudo (RN15).
 */
@Repository
public class OccurrenceRepository {

    /**
     * Preenche praia/setor a partir do ponto: primeiro o setor ativo que
     * contém o ponto (o de menor {@code display_order}), depois a área ativa.
     * O responsável é quem deu o ack do chamado.
     */
    private static final String OPEN_FROM_CALL_SQL = """
            WITH opened AS (
                INSERT INTO occurrences (
                    origin, emergency_call_id, nature, status, occurred_at,
                    location, location_accuracy_m, beach_id, beach_sector_id, post_id,
                    responder_id, ciosp_status, created_at, updated_at)
                SELECT 'SOS_CITIZEN', c.id, 'RESCUE', 'IN_PROGRESS', c.created_at,
                       c.location, c.accuracy_meters,
                       COALESCE(sector.beach_id, area.id),
                       sector.id,
                       c.assigned_post_id,
                       (SELECT d.recipient_user_id
                        FROM emergency_dispatches d
                        WHERE d.emergency_call_id = c.id
                          AND d.status = 'ACKNOWLEDGED'
                        ORDER BY d.created_at DESC
                        LIMIT 1),
                       'PENDING', NOW(), NOW()
                FROM emergency_calls c
                LEFT JOIN LATERAL (
                    SELECT s.id, s.beach_id
                    FROM beach_sectors s
                    WHERE s.active
                      AND ST_Contains(s.geometry, CAST(c.location AS geometry))
                    ORDER BY s.display_order, s.id
                    LIMIT 1
                ) sector ON TRUE
                LEFT JOIN LATERAL (
                    SELECT b.id
                    FROM beaches b
                    WHERE b.active
                      AND ST_Contains(b.boundary, CAST(c.location AS geometry))
                    ORDER BY b.id
                    LIMIT 1
                ) area ON TRUE
                WHERE c.acknowledged_at IS NOT NULL
                  AND c.status IN ('ACKNOWLEDGED', 'EN_ROUTE', 'ON_SCENE')
                  AND (%s)
                ON CONFLICT (emergency_call_id) DO NOTHING
                RETURNING id
            )
            INSERT INTO occurrence_events (occurrence_id, kind, detail)
            SELECT id, 'OPENED', jsonb_build_object('origin', 'SOS_CITIZEN')
            FROM opened
            """;

    /** Escopo de leitura/escrita: o responsável, ou coordenação/TI. */
    private static final String SCOPE_SQL = """
            (
                :allScopes = TRUE
                OR o.responder_id = :userId
                OR (o.responder_id IS NULL AND EXISTS (
                    SELECT 1
                    FROM emergency_dispatches d
                    WHERE d.emergency_call_id = o.emergency_call_id
                      AND d.recipient_user_id = :userId
                      AND d.status = 'ACKNOWLEDGED'))
            )
            """;

    private static final String VIEW_COLUMNS = """
            o.id, o.number, o.origin, o.nature, o.status, o.outcome_type,
            o.occurred_at, o.created_at, o.closed_at,
            o.emergency_call_id, o.beach_id, b.name AS beach_name,
            o.beach_sector_id, s.name AS sector_name, s.display_order,
            o.post_id, p.name AS post_name,
            o.responder_id, ru.name AS responder_name,
            ST_Y(CAST(o.location AS geometry)) AS latitude,
            ST_X(CAST(o.location AS geometry)) AS longitude,
            o.location_accuracy_m, o.ciosp_status,
            c.acknowledged_at
            """;

    private static final String FROM_SQL = """
            FROM occurrences o
            LEFT JOIN beaches b ON b.id = o.beach_id
            LEFT JOIN beach_sectors s ON s.id = o.beach_sector_id
            LEFT JOIN lifeguard_posts p ON p.id = o.post_id
            LEFT JOIN operational_users ru ON ru.id = o.responder_id
            LEFT JOIN emergency_calls c ON c.id = o.emergency_call_id
            """;

    private static final String DETAIL_COLUMNS = """
            , o.summary, o.outcome_notes, o.reference_text, o.external_protocol,
            o.ciosp_protocol, o.ciosp_registered_at, o.closed_by,
            cu.name AS closed_by_name,
            o.victim_name, o.victim_age, o.victim_contact, o.victim_condition,
            o.version
            """;

    private static final String DETAIL_FROM_SQL = FROM_SQL + """
            LEFT JOIN operational_users cu ON cu.id = o.closed_by
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final SensitiveDataProtector protector;
    private final ObjectMapper objectMapper;

    public OccurrenceRepository(
            NamedParameterJdbcTemplate jdbc,
            SensitiveDataProtector protector,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.protector = protector;
        this.objectMapper = objectMapper;
    }

    /**
     * Abre a ocorrência do chamado assumido. Idempotente: se ela já existe, o
     * {@code ON CONFLICT} não escreve nada — nem o evento de abertura.
     *
     * @return quantas ocorrências foram criadas nesta chamada
     */
    public int openFromAcknowledgedCall(UUID callId) {
        return jdbc.update(
                OPEN_FROM_CALL_SQL.formatted("c.id = :callId"),
                Map.of("callId", callId));
    }

    /**
     * Rede de segurança do listener: abre o que ficou faltando para chamados
     * assumidos antes do instante informado (RF32).
     */
    public int openMissingAcknowledged(Instant acknowledgedBefore) {
        // O driver não infere o tipo de `java.time.Instant` no parâmetro: o
        // instante vai como OffsetDateTime (timestamptz), como no resto do SQL.
        return jdbc.update(
                OPEN_FROM_CALL_SQL.formatted("c.acknowledged_at < :before"),
                Map.of("before", OffsetDateTime.ofInstant(acknowledgedBefore, ZoneOffset.UTC)));
    }

    public List<OccurrenceView> findAll(
            UUID userId, boolean allScopes, OccurrenceStatus status, int limit) {
        MapSqlParameterSource params = scopeParams(userId, allScopes)
                .addValue("status", status == null ? null : status.name(), Types.VARCHAR)
                .addValue("limit", limit);
        return jdbc.query(
                "SELECT " + VIEW_COLUMNS + FROM_SQL
                        + " WHERE " + SCOPE_SQL
                        + " AND (:status IS NULL OR o.status = :status)"
                        + " ORDER BY o.occurred_at DESC, o.number DESC LIMIT :limit",
                params,
                (rs, rowNum) -> view(rs));
    }

    public Optional<OccurrenceDetail> findDetail(
            UUID occurrenceId, UUID userId, boolean allScopes, boolean forUpdate) {
        MapSqlParameterSource params = scopeParams(userId, allScopes)
                .addValue("occurrenceId", occurrenceId);
        String sql = "SELECT " + VIEW_COLUMNS + DETAIL_COLUMNS + DETAIL_FROM_SQL
                + " WHERE o.id = :occurrenceId AND " + SCOPE_SQL
                + (forUpdate ? " FOR UPDATE OF o" : "");
        return jdbc.query(sql, params, (rs, rowNum) -> detail(rs)).stream().findFirst();
    }

    /**
     * Grava o encerramento. O serviço já resolveu o desfecho, o status e a
     * pendência do CIOSP; aqui só entram valores já cifrados.
     */
    public int close(UUID occurrenceId, CloseWrite data) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", occurrenceId)
                .addValue("status", data.status().name(), Types.VARCHAR)
                .addValue("nature", data.nature() == null ? null : data.nature().name(),
                        Types.VARCHAR)
                .addValue("summary", data.summary(), Types.VARCHAR)
                .addValue("outcome", data.outcome().name(), Types.VARCHAR)
                .addValue("outcomeNotes", data.outcomeNotes(), Types.VARCHAR)
                .addValue("referenceText", data.referenceText(), Types.VARCHAR)
                .addValue("externalProtocol", data.externalProtocol(), Types.VARCHAR)
                .addValue("ciospStatus", data.ciospStatus().name(), Types.VARCHAR)
                .addValue("ciospProtocol", data.ciospProtocol(), Types.VARCHAR)
                .addValue("actorId", data.actorId(), Types.OTHER)
                .addValue("victimName", data.victimName(), Types.VARCHAR)
                .addValue("victimAge", data.victimAge(), Types.VARCHAR)
                .addValue("victimContact", data.victimContact(), Types.VARCHAR)
                .addValue("victimCondition", data.victimCondition(), Types.VARCHAR);
        return jdbc.update("""
                UPDATE occurrences
                SET status = :status,
                    nature = COALESCE(CAST(:nature AS varchar), nature),
                    summary = :summary,
                    outcome_type = :outcome,
                    outcome_notes = COALESCE(CAST(:outcomeNotes AS text), outcome_notes),
                    reference_text = COALESCE(CAST(:referenceText AS text), reference_text),
                    external_protocol = COALESCE(
                        CAST(:externalProtocol AS varchar), external_protocol),
                    ciosp_status = :ciospStatus,
                    ciosp_protocol = COALESCE(
                        CAST(:ciospProtocol AS varchar), ciosp_protocol),
                    ciosp_registered_at = CASE WHEN :ciospStatus = 'REGISTERED'
                        THEN NOW() ELSE ciosp_registered_at END,
                    ciosp_registered_by = CASE WHEN :ciospStatus = 'REGISTERED'
                        THEN :actorId ELSE ciosp_registered_by END,
                    victim_name = COALESCE(CAST(:victimName AS text), victim_name),
                    victim_age = COALESCE(CAST(:victimAge AS text), victim_age),
                    victim_contact = COALESCE(CAST(:victimContact AS text), victim_contact),
                    victim_condition = COALESCE(
                        CAST(:victimCondition AS text), victim_condition),
                    closed_at = NOW(),
                    closed_by = :actorId,
                    updated_at = NOW(),
                    version = version + 1
                WHERE id = :id
                """, params);
    }

    /**
     * Edição parcial: só as colunas presentes no pedido entram no {@code SET}.
     * Isso evita sobrescrever dado que a tela não mostrou (vítima mascarada).
     */
    public int update(UUID occurrenceId, UpdateWrite data, UUID actorId) {
        StringBuilder sql = new StringBuilder(
                "UPDATE occurrences SET updated_at = NOW(), version = version + 1");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", occurrenceId);

        if (data.nature() != null) {
            sql.append(", nature = :nature");
            params.addValue("nature", data.nature().name(), Types.VARCHAR);
        }
        if (data.summary() != null) {
            sql.append(", summary = :summary");
            params.addValue("summary", data.summary(), Types.VARCHAR);
        }
        if (data.outcomeNotes() != null) {
            sql.append(", outcome_notes = :outcomeNotes");
            params.addValue("outcomeNotes", data.outcomeNotes(), Types.VARCHAR);
        }
        if (data.referenceText() != null) {
            sql.append(", reference_text = :referenceText");
            params.addValue("referenceText", data.referenceText(), Types.VARCHAR);
        }
        if (data.externalProtocol() != null) {
            sql.append(", external_protocol = :externalProtocol");
            params.addValue("externalProtocol", data.externalProtocol(), Types.VARCHAR);
        }
        if (data.victimName() != null) {
            sql.append(", victim_name = :victimName");
            params.addValue("victimName", data.victimName(), Types.VARCHAR);
        }
        if (data.victimAge() != null) {
            sql.append(", victim_age = :victimAge");
            params.addValue("victimAge", data.victimAge(), Types.VARCHAR);
        }
        if (data.victimContact() != null) {
            sql.append(", victim_contact = :victimContact");
            params.addValue("victimContact", data.victimContact(), Types.VARCHAR);
        }
        if (data.victimCondition() != null) {
            sql.append(", victim_condition = :victimCondition");
            params.addValue("victimCondition", data.victimCondition(), Types.VARCHAR);
        }
        if (data.ciospProtocol() != null) {
            sql.append(", ciosp_protocol = CAST(:ciospProtocol AS varchar)");
            params.addValue("ciospProtocol", data.ciospProtocol(), Types.VARCHAR);
        }
        if (data.ciospStatus() != null) {
            sql.append(", ciosp_status = :ciospStatus");
            params.addValue("ciospStatus", data.ciospStatus().name(), Types.VARCHAR);
            if (data.ciospStatus() == CiospStatus.REGISTERED) {
                sql.append(", ciosp_registered_at = COALESCE(ciosp_registered_at, NOW())")
                        .append(", ciosp_registered_by = COALESCE(ciosp_registered_by, :actorId)");
                params.addValue("actorId", actorId, Types.OTHER);
            } else {
                sql.append(", ciosp_registered_at = NULL, ciosp_registered_by = NULL");
            }
        }
        sql.append(" WHERE id = :id");
        return jdbc.update(sql.toString(), params);
    }

    public void appendEvent(
            UUID occurrenceId, String kind, UUID actorId, Map<String, Object> detail) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("occurrenceId", occurrenceId)
                .addValue("kind", kind, Types.VARCHAR)
                .addValue("actorId", actorId, Types.OTHER)
                .addValue("detail", json(detail), Types.VARCHAR);
        jdbc.update("""
                INSERT INTO occurrence_events (occurrence_id, kind, actor_id, detail)
                VALUES (:occurrenceId, :kind, :actorId, CAST(:detail AS jsonb))
                """, params);
    }

    private MapSqlParameterSource scopeParams(UUID userId, boolean allScopes) {
        return new MapSqlParameterSource()
                .addValue("userId", userId, Types.OTHER)
                .addValue("allScopes", allScopes, Types.BOOLEAN);
    }

    private OccurrenceView view(ResultSet rs) throws SQLException {
        return new OccurrenceView(
                rs.getObject("id", UUID.class),
                rs.getLong("number"),
                OccurrenceOrigin.valueOf(rs.getString("origin")),
                OccurrenceNature.valueOf(rs.getString("nature")),
                OccurrenceStatus.valueOf(rs.getString("status")),
                enumOrNull(rs.getString("outcome_type"), OccurrenceOutcome::valueOf),
                instant(rs, "occurred_at"),
                instant(rs, "created_at"),
                instant(rs, "acknowledged_at"),
                instant(rs, "closed_at"),
                rs.getObject("emergency_call_id", UUID.class),
                rs.getObject("beach_id", UUID.class),
                rs.getString("beach_name"),
                rs.getObject("beach_sector_id", UUID.class),
                sectorLabel(rs.getString("sector_name"), rs.getInt("display_order")),
                rs.getObject("post_id", UUID.class),
                rs.getString("post_name"),
                rs.getObject("responder_id", UUID.class),
                decryptName(rs.getString("responder_name")),
                rs.getObject("latitude", Double.class),
                rs.getObject("longitude", Double.class),
                rs.getObject("location_accuracy_m", BigDecimal.class) == null
                        ? null
                        : rs.getObject("location_accuracy_m", BigDecimal.class).doubleValue(),
                CiospStatus.valueOf(rs.getString("ciosp_status")));
    }

    private OccurrenceDetail detail(ResultSet rs) throws SQLException {
        VictimData victim = new VictimData(
                rs.getString("victim_name"),
                rs.getString("victim_age"),
                rs.getString("victim_contact"),
                rs.getString("victim_condition"));
        return new OccurrenceDetail(
                view(rs),
                decrypt(rs.getString("summary"), SensitiveDataProtector.AAD_OCCURRENCE_SUMMARY),
                decrypt(rs.getString("outcome_notes"),
                        SensitiveDataProtector.AAD_OCCURRENCE_OUTCOME),
                rs.getString("reference_text"),
                rs.getString("external_protocol"),
                rs.getString("ciosp_protocol"),
                instant(rs, "ciosp_registered_at"),
                rs.getObject("closed_by", UUID.class),
                decryptName(rs.getString("closed_by_name")),
                victim,
                rs.getLong("version"));
    }

    private String decrypt(String value, String aad) {
        return value == null ? null : protector.decryptOrPlaintext(value, aad);
    }

    /**
     * O nome do operador é cifrado pelo converter JPA; a leitura por SQL direto
     * precisa decifrar com o mesmo AAD.
     */
    private String decryptName(String value) {
        return decrypt(value, SensitiveDataProtector.AAD_JPA_FIELD);
    }

    private String json(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao serializar o histórico da ocorrência",
                    exception);
        }
    }

    private static <T> T enumOrNull(String value, java.util.function.Function<String, T> parser) {
        return value == null ? null : parser.apply(value);
    }

    /** {@code getObject(..., Instant.class)} não é suportado pelo driver. */
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /** Nome do setor é rótulo: sem nome, a ordem do mapeamento identifica. */
    private static String sectorLabel(String name, int displayOrder) {
        if (name == null || name.isBlank()) {
            return displayOrder > 0 ? "Setor " + displayOrder : null;
        }
        return name;
    }

    /** Dados de vítima como estão no banco: cifrados, decifrados só na revelação. */
    public record VictimData(String name, String age, String contact, String condition) {

        public boolean recorded() {
            return name != null || age != null || contact != null || condition != null;
        }
    }

    public record OccurrenceDetail(
            OccurrenceView view,
            String summary,
            String outcomeNotes,
            String referenceText,
            String externalProtocol,
            String ciospProtocol,
            Instant ciospRegisteredAt,
            UUID closedBy,
            String closedByName,
            VictimData victim,
            long version) {
    }

    public record CloseWrite(
            OccurrenceStatus status,
            OccurrenceNature nature,
            OccurrenceOutcome outcome,
            CiospStatus ciospStatus,
            String summary,
            String outcomeNotes,
            String referenceText,
            String externalProtocol,
            String ciospProtocol,
            String victimName,
            String victimAge,
            String victimContact,
            String victimCondition,
            UUID actorId) {
    }

    public record UpdateWrite(
            OccurrenceNature nature,
            String summary,
            String outcomeNotes,
            String referenceText,
            String externalProtocol,
            String ciospProtocol,
            CiospStatus ciospStatus,
            String victimName,
            String victimAge,
            String victimContact,
            String victimCondition) {
    }
}
