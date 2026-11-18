package com.maraberto.operations.application;

import com.maraberto.identity.api.OperatorProfile;
import com.maraberto.identity.api.OperatorProfiles;
import com.maraberto.operations.api.ManagementTeamView;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura da equipe em plantão para o mapa de situação da coordenação.
 *
 * <p>A consulta é a única fonte da lista lateral e dos pins de guarda-vidas
 * (RF40/ADR-026). Ela devolve **um** ponto por plantão — a última posição
 * sobrescrita pelo console — e só plantões abertos com lease vivo: encerrar o
 * turno remove a linha na leitura seguinte. Contadores são do dia do servidor.
 *
 * <p>A posição mais velha que {@code position-stale-after} continua na linha,
 * marcada como {@code stale}: a coordenação precisa ver "sem sinal há X min" em
 * vez de um ponto que parece atual. Nada aqui é gravado, logado ou cacheado.
 */
@Service
public class ManagementTeamService {

    private static final String TEAM_SQL = """
            SELECT s.id AS shift_id,
                   s.user_id,
                   s.started_at,
                   s.last_seen_at,
                   s.last_position_at,
                   s.last_position_accuracy_m,
                   ST_Y(CAST(s.last_position AS geometry)) AS latitude,
                   ST_X(CAST(s.last_position AS geometry)) AS longitude,
                   COALESCE(p.name, m.name) AS station_name,
                   COALESCE(b.name, sector.beach_name) AS beach_name,
                   sector.sector_name,
                   (s.last_position_at < NOW() - make_interval(secs => :staleSeconds))
                       AS position_stale,
                   (s.last_seen_at >= NOW() - make_interval(secs => :leaseSeconds))
                       AS lease_alive,
                   counters.occurrences_today,
                   counters.field_attended_today,
                   counters.ciosp_pending,
                   signals.signals_today
              FROM shift_sessions s
              LEFT JOIN lifeguard_posts p ON p.id = s.post_id
              LEFT JOIN beaches b ON b.id = p.beach_id
              LEFT JOIN mobile_bases m ON m.id = s.mobile_base_id
              LEFT JOIN LATERAL (
                  SELECT bs.name AS sector_name,
                         sb.name AS beach_name
                    FROM beach_sectors bs
                    JOIN beaches sb ON sb.id = bs.beach_id
                   WHERE s.last_position IS NOT NULL
                     AND bs.active
                     AND ST_Contains(bs.geometry, CAST(s.last_position AS geometry))
                   ORDER BY bs.display_order, bs.id
                   LIMIT 1
              ) sector ON TRUE
              LEFT JOIN LATERAL (
                  SELECT COUNT(*) FILTER (
                             WHERE o.occurred_at >= date_trunc('day', NOW())) AS occurrences_today,
                         COUNT(*) FILTER (
                             WHERE o.occurred_at >= date_trunc('day', NOW())
                               AND o.origin = 'FIELD_ATTENDED') AS field_attended_today,
                         COUNT(*) FILTER (
                             WHERE o.ciosp_status = 'PENDING'
                               AND o.status IN ('OPEN', 'IN_PROGRESS')) AS ciosp_pending
                    FROM occurrences o
                   WHERE o.responder_id = s.user_id
              ) counters ON TRUE
              LEFT JOIN LATERAL (
                  SELECT COUNT(*) AS signals_today
                    FROM flag_updates f
                   WHERE f.set_by = s.user_id
                     AND f.created_at >= date_trunc('day', NOW())
              ) signals ON TRUE
             WHERE s.ended_at IS NULL
               AND s.last_seen_at >= NOW() - make_interval(secs => :leaseSeconds)
             ORDER BY s.last_position_at ASC NULLS FIRST, s.started_at ASC
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final OperatorProfiles operatorProfiles;
    private final Duration leaseTimeout;
    private final Duration positionStaleAfter;

    public ManagementTeamService(
            NamedParameterJdbcTemplate jdbc,
            OperatorProfiles operatorProfiles,
            @Value("${maraberto.dispatch.shift-idle-timeout:PT15M}") Duration leaseTimeout,
            @Value("${maraberto.dispatch.position-stale-after:PT30M}")
            Duration positionStaleAfter) {
        this.jdbc = jdbc;
        this.operatorProfiles = operatorProfiles;
        this.leaseTimeout = leaseTimeout;
        this.positionStaleAfter = positionStaleAfter;
    }

    @Transactional(readOnly = true)
    public ManagementTeamView team() {
        List<Row> rows = jdbc.query(TEAM_SQL, new MapSqlParameterSource()
                        .addValue("leaseSeconds", leaseTimeout.toSeconds())
                        .addValue("staleSeconds", positionStaleAfter.toSeconds()),
                (rs, rowNumber) -> new Row(
                        rs.getObject("shift_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("station_name"),
                        rs.getString("beach_name"),
                        rs.getString("sector_name"),
                        instant(rs, "started_at"),
                        instant(rs, "last_seen_at"),
                        rs.getBoolean("lease_alive"),
                        position(rs),
                        rs.getInt("occurrences_today"),
                        rs.getInt("field_attended_today"),
                        rs.getInt("signals_today"),
                        rs.getInt("ciosp_pending")));

        Map<UUID, OperatorProfile> profiles = profilesOf(rows);
        List<ManagementTeamView.Member> members = rows.stream()
                .map(row -> row.toView(profiles.get(row.userId())))
                .toList();
        return new ManagementTeamView(Instant.now(), members);
    }

    private Map<UUID, OperatorProfile> profilesOf(List<Row> rows) {
        List<UUID> userIds = rows.stream().map(Row::userId).distinct().toList();
        Map<UUID, OperatorProfile> profiles = new LinkedHashMap<>();
        for (OperatorProfile profile : operatorProfiles.findAll(userIds)) {
            profiles.put(profile.id(), profile);
        }
        return profiles;
    }

    private static ManagementTeamView.Position position(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        Double latitude = rs.getObject("latitude", Double.class);
        Double longitude = rs.getObject("longitude", Double.class);
        if (latitude == null || longitude == null) {
            return null;
        }
        return new ManagementTeamView.Position(
                latitude,
                longitude,
                rs.getObject("last_position_accuracy_m", Double.class),
                instant(rs, "last_position_at"),
                rs.getBoolean("position_stale"));
    }

    private static Instant instant(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /**
     * Linha crua da consulta; o nome vem da porta do {@code identity}, porque o
     * cadastro cifra o nome e só o dono do dado o decifra.
     */
    private record Row(
            UUID shiftId,
            UUID userId,
            String stationName,
            String beachName,
            String sectorLabel,
            Instant onShiftSince,
            Instant lastSeenAt,
            boolean leaseAlive,
            ManagementTeamView.Position lastPosition,
            int occurrencesToday,
            int fieldAttendedToday,
            int signalsToday,
            int ciospPending) {

        ManagementTeamView.Member toView(OperatorProfile profile) {
            return new ManagementTeamView.Member(
                    shiftId,
                    userId,
                    profile == null ? "Operador" : profile.name(),
                    profile == null ? null : profile.badgeNumber(),
                    stationName,
                    beachName,
                    sectorLabel,
                    onShiftSince,
                    lastSeenAt,
                    leaseAlive,
                    lastPosition,
                    occurrencesToday,
                    fieldAttendedToday,
                    signalsToday,
                    ciospPending);
        }
    }
}
