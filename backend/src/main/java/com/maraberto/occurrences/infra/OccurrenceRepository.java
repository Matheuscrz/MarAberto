package com.maraberto.occurrences.infra;

import com.maraberto.occurrences.api.OccurrenceView;
import com.maraberto.occurrences.domain.CiospStatus;
import com.maraberto.occurrences.domain.OccurrenceNature;
import com.maraberto.occurrences.domain.OccurrenceOrigin;
import com.maraberto.occurrences.domain.OccurrenceStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * <p>A visibilidade do guarda-vidas é a do seu plantão (posto e praia) mais os
 * chamados que ele mesmo assumiu; coordenação e TI veem tudo (RN15).
 */
@Repository
public class OccurrenceRepository {

    /**
     * Preenche praia/setor a partir do ponto: primeiro o setor ativo que
     * contém o ponto (o de menor {@code display_order}), depois a área ativa.
     */
    private static final String OPEN_FROM_CALL_SQL = """
            WITH opened AS (
                INSERT INTO occurrences (
                    origin, emergency_call_id, nature, status, occurred_at,
                    location, location_accuracy_m, beach_id, beach_sector_id, post_id,
                    ciosp_status, created_at, updated_at)
                SELECT 'SOS_CITIZEN', c.id, 'RESCUE', 'IN_PROGRESS', c.created_at,
                       c.location, c.accuracy_meters,
                       COALESCE(sector.beach_id, area.id),
                       sector.id,
                       c.assigned_post_id,
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

    private static final String VIEW_SQL = """
            SELECT o.id, o.number, o.origin, o.nature, o.status,
                   o.occurred_at, o.created_at, o.closed_at,
                   o.emergency_call_id, o.beach_id, b.name AS beach_name,
                   o.beach_sector_id, s.name AS sector_name, s.display_order,
                   o.post_id, p.name AS post_name,
                   ST_Y(CAST(o.location AS geometry)) AS latitude,
                   ST_X(CAST(o.location AS geometry)) AS longitude,
                   o.location_accuracy_m, o.ciosp_status,
                   c.acknowledged_at
            FROM occurrences o
            LEFT JOIN beaches b ON b.id = o.beach_id
            LEFT JOIN beach_sectors s ON s.id = o.beach_sector_id
            LEFT JOIN lifeguard_posts p ON p.id = o.post_id
            LEFT JOIN emergency_calls c ON c.id = o.emergency_call_id
            WHERE (
                :allScopes = TRUE
                OR o.post_id = (SELECT sh.post_id FROM shift_sessions sh
                                WHERE sh.user_id = :userId AND sh.ended_at IS NULL)
                OR o.beach_id = (SELECT sh.beach_id FROM shift_sessions sh
                                 WHERE sh.user_id = :userId AND sh.ended_at IS NULL)
                OR EXISTS (SELECT 1 FROM emergency_dispatches d
                           WHERE d.emergency_call_id = o.emergency_call_id
                             AND d.recipient_user_id = :userId
                             AND d.status = 'ACKNOWLEDGED')
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OccurrenceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

    public List<OccurrenceView> findAll(UUID userId, boolean allScopes, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("allScopes", allScopes)
                .addValue("limit", limit);
        return jdbc.query(
                VIEW_SQL + " ORDER BY o.occurred_at DESC, o.number DESC LIMIT :limit",
                params,
                (rs, rowNum) -> view(rs));
    }

    public Optional<OccurrenceView> findById(UUID occurrenceId, UUID userId, boolean allScopes) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("allScopes", allScopes)
                .addValue("occurrenceId", occurrenceId);
        return jdbc.query(
                        VIEW_SQL + " AND o.id = :occurrenceId",
                        params,
                        (rs, rowNum) -> view(rs))
                .stream()
                .findFirst();
    }

    private static OccurrenceView view(ResultSet rs) throws SQLException {
        return new OccurrenceView(
                rs.getObject("id", UUID.class),
                rs.getLong("number"),
                OccurrenceOrigin.valueOf(rs.getString("origin")),
                OccurrenceNature.valueOf(rs.getString("nature")),
                OccurrenceStatus.valueOf(rs.getString("status")),
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
                rs.getObject("latitude", Double.class),
                rs.getObject("longitude", Double.class),
                rs.getObject("location_accuracy_m", java.math.BigDecimal.class) == null
                        ? null
                        : rs.getObject("location_accuracy_m", java.math.BigDecimal.class)
                                .doubleValue(),
                CiospStatus.valueOf(rs.getString("ciosp_status")));
    }

    /** {@code getObject(..., Instant.class)} não é suportado pelo driver. */
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /** Nome do setor é rótulo: sem nome, a ordem do mapeamento identifica. */
    private static String sectorLabel(String name, int displayOrder) {
        if (name == null || name.isBlank()) {
            return displayOrder > 0 ? "Setor " + displayOrder : null;
        }
        return name;
    }
}
