package com.maraberto.operations.application;

import com.maraberto.operations.api.ShiftTarget;
import com.maraberto.operations.api.ShiftTargetKind;
import com.maraberto.operations.domain.ShiftAlreadyOpenException;
import com.maraberto.operations.domain.ShiftNotFoundException;
import com.maraberto.operations.domain.ShiftSession;
import com.maraberto.operations.infra.ShiftSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida do plantão do operador.
 *
 * <p>Abrir o plantão é o que torna o operador elegível para receber o SOS
 * (ADR-018). A posição informada aqui é a última posição conhecida do turno,
 * usada para elegibilidade e para o painel autorizado — não é rastreamento
 * contínuo (RN17).
 */
@Service
public class ShiftService {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Lista de destinos de plantão. Só estruturas ativas entram: plantar em uma
     * estrutura desativada deixaria o operador fora da seleção do SOS sem
     * qualquer aviso na tela.
     */
    private static final String TARGETS_SQL = """
            SELECT p.id,
                   'POST' AS kind,
                   p.name,
                   b.name AS beach_name,
                   ST_Y(p.location::geometry) AS latitude,
                   ST_X(p.location::geometry) AS longitude,
                   p.coverage_radius_m
            FROM lifeguard_posts p
            JOIN beaches b ON b.id = p.beach_id
            WHERE p.active
            UNION ALL
            SELECT m.id,
                   'MOBILE_BASE' AS kind,
                   m.name,
                   NULL AS beach_name,
                   ST_Y(m.location::geometry) AS latitude,
                   ST_X(m.location::geometry) AS longitude,
                   m.coverage_radius_m
            FROM mobile_bases m
            WHERE m.active
            ORDER BY kind, name
            """;

    private final ShiftSessionRepository shiftSessions;
    private final NamedParameterJdbcTemplate jdbc;

    public ShiftService(ShiftSessionRepository shiftSessions, NamedParameterJdbcTemplate jdbc) {
        this.shiftSessions = shiftSessions;
        this.jdbc = jdbc;
    }

    @Transactional
    public ShiftSession open(UUID userId, OpenShiftCommand command) {
        if ((command.postId() == null) == (command.mobileBaseId() == null)) {
            throw new IllegalArgumentException(
                    "Informe exatamente um destino de plantão: posto fixo ou base móvel");
        }
        requireCompletePair(command.latitude(), command.longitude());
        requireActiveTarget(command.postId(), command.mobileBaseId());
        if (shiftSessions.existsByUserIdAndEndedAtIsNull(userId)) {
            throw new ShiftAlreadyOpenException();
        }

        ShiftSession shift = new ShiftSession();
        shift.setUserId(userId);
        shift.setPostId(command.postId());
        shift.setMobileBaseId(command.mobileBaseId());
        shift.setStartedAt(Instant.now());
        if (command.latitude() != null && command.longitude() != null) {
            shift.moveTo(point(command.latitude(), command.longitude()),
                    Instant.now(), command.accuracyMeters());
        }
        return shiftSessions.save(shift);
    }

    @Transactional(readOnly = true)
    public Optional<ShiftSession> current(UUID userId) {
        return shiftSessions.findByUserIdAndEndedAtIsNull(userId);
    }

    /**
     * Postos fixos e bases móveis ativos, ordenados por tipo e nome.
     *
     * <p>A lista é a única fonte de verdade do console: o operador só abre
     * plantão em uma estrutura existente, porque é a posição dela que entra na
     * seleção espacial do SOS.
     */
    @Transactional(readOnly = true)
    public List<ShiftTarget> targets() {
        return jdbc.query(TARGETS_SQL, (resultSet, rowNumber) -> new ShiftTarget(
                resultSet.getObject("id", UUID.class),
                ShiftTargetKind.valueOf(resultSet.getString("kind")),
                resultSet.getString("name"),
                resultSet.getString("beach_name"),
                resultSet.getDouble("latitude"),
                resultSet.getDouble("longitude"),
                resultSet.getObject("coverage_radius_m", Integer.class)));
    }

    @Transactional
    public ShiftSession move(UUID userId, double latitude, double longitude, Double accuracyMeters) {
        ShiftSession shift = shiftSessions.findByUserIdAndEndedAtIsNull(userId)
                .orElseThrow(ShiftNotFoundException::new);
        shift.moveTo(point(latitude, longitude), Instant.now(), accuracyMeters);
        return shift;
    }

    @Transactional
    public ShiftSession close(UUID userId) {
        ShiftSession shift = shiftSessions.findByUserIdAndEndedAtIsNull(userId)
                .orElseThrow(ShiftNotFoundException::new);
        shift.end(Instant.now());
        return shift;
    }

    private void requireActiveTarget(UUID postId, UUID mobileBaseId) {
        String sql = postId != null
                ? "SELECT active FROM lifeguard_posts WHERE id = :id"
                : "SELECT active FROM mobile_bases WHERE id = :id";
        UUID id = postId != null ? postId : mobileBaseId;
        Boolean active = jdbc.query(sql, new MapSqlParameterSource("id", id),
                resultSet -> resultSet.next() ? resultSet.getBoolean("active") : null);
        if (!Boolean.TRUE.equals(active)) {
            throw new IllegalArgumentException(
                    "Posto ou base informado não existe ou está inativo");
        }
    }

    /**
     * Coordenada é sempre o par: meia posição não pode virar distância no
     * PostGIS e passaria despercebida até o chamado.
     */
    private static void requireCompletePair(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException(
                    "Informe latitude e longitude juntas para registrar a posição do plantão");
        }
    }

    private static org.locationtech.jts.geom.Point point(double latitude, double longitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    public record OpenShiftCommand(
            UUID postId,
            UUID mobileBaseId,
            Double latitude,
            Double longitude,
            Double accuracyMeters) {
    }
}
