package com.maraberto.operations.application;

import com.maraberto.operations.api.ShiftTarget;
import com.maraberto.operations.api.ShiftTargetKind;
import com.maraberto.operations.domain.ShiftAlreadyOpenException;
import com.maraberto.operations.domain.ShiftEndReason;
import com.maraberto.operations.domain.ShiftNotFoundException;
import com.maraberto.operations.domain.ShiftSession;
import com.maraberto.operations.infra.ShiftSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger log = LoggerFactory.getLogger(ShiftService.class);

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
                   p.position_updated_at,
                   p.position_accuracy_m,
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
                   m.position_updated_at,
                   m.position_accuracy_m,
                   m.coverage_radius_m
            FROM mobile_bases m
            WHERE m.active
            ORDER BY kind, name
            """;

    private final ShiftSessionRepository shiftSessions;
    private final NamedParameterJdbcTemplate jdbc;
    private final Duration idleTimeout;

    public ShiftService(
            ShiftSessionRepository shiftSessions,
            NamedParameterJdbcTemplate jdbc,
            @Value("${maraberto.dispatch.shift-idle-timeout:PT15M}") Duration idleTimeout) {
        this.shiftSessions = shiftSessions;
        this.jdbc = jdbc;
        this.idleTimeout = idleTimeout;
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
        shift.setLastSeenAt(shift.getStartedAt());
        if (command.latitude() != null && command.longitude() != null) {
            shift.moveTo(point(command.latitude(), command.longitude()),
                    Instant.now(), command.accuracyMeters());
        }
        return shiftSessions.save(shift);
    }

    /**
     * Plantão aberto do operador. Cada leitura é sinal de vida: o console
     * consulta o plantão ao abrir e ao voltar para o primeiro plano, o que
     * renova o prazo do job de inatividade.
     */
    @Transactional
    public Optional<ShiftSession> current(UUID userId) {
        Optional<ShiftSession> current = shiftSessions.findByUserIdAndEndedAtIsNull(userId);
        Instant now = Instant.now();
        Optional<ShiftSession> alive = current.filter(shift -> !expireIfIdle(shift, now));
        alive.ifPresent(shift -> shift.touch(now));
        return alive;
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
                instant(resultSet, "position_updated_at"),
                accuracy(resultSet, "position_accuracy_m"),
                resultSet.getObject("coverage_radius_m", Integer.class)));
    }

    private static Instant instant(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Double accuracy(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        java.math.BigDecimal value = resultSet.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    @Transactional
    public ShiftSession move(UUID userId, double latitude, double longitude, Double accuracyMeters) {
        ShiftSession shift = requireCurrent(userId);
        shift.moveTo(point(latitude, longitude), Instant.now(), accuracyMeters);
        return shift;
    }

    /**
     * Sinal de vida barato do plantão.
     *
     * <p>O console envia a posição a cada 5 min; quando o GPS não está
     * disponível (ou não faz sentido reenviar), este batimento mantém o turno
     * aberto sem payload e sem consulta espacial. É o que separa 1 requisição
     * minúscula a cada 5 min de um polling caro.
     */
    @Transactional
    public Instant heartbeat(UUID userId) {
        ShiftSession shift = requireCurrent(userId);
        Instant now = Instant.now();
        shift.touch(now);
        return now;
    }

    @Transactional
    public ShiftSession close(UUID userId) {
        ShiftSession shift = shiftSessions.findByUserIdAndEndedAtIsNull(userId)
                .orElseThrow(ShiftNotFoundException::new);
        Instant now = Instant.now();
        if (expireIfIdle(shift, now)) {
            // Já caiu por inatividade: encerrar de novo não muda nada, mas o
            // operador recebe o estado final em vez de um 404 confuso.
            return shift;
        }
        shift.end(now, ShiftEndReason.MANUAL);
        return shift;
    }

    /**
     * Encerra plantões sem sinal de vida.
     *
     * <p>Correção não depende deste método: a elegibilidade do SOS e a leitura
     * do plantão já aplicam o prazo (expiração preguiçosa). O job existe só para
     * carimbar {@code ended_at}/{@code ended_reason} nas linhas e manter o painel
     * da coordenação e os relatórios honestos — roda com intervalo largo e toca
     * apenas as linhas vencidas (índice parcial da V9).
     *
     * @return quantidade de plantões encerrados nesta passagem
     */
    @Transactional
    public int closeIdleShifts() {
        String sql = """
                UPDATE shift_sessions
                SET ended_at = NOW(),
                    ended_reason = :reason,
                    updated_at = NOW()
                WHERE ended_at IS NULL
                  AND last_seen_at < NOW() - make_interval(secs => :seconds)
                """;
        int closed = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("reason", ShiftEndReason.IDLE_TIMEOUT.name())
                .addValue("seconds", idleTimeout.toSeconds()));
        if (closed > 0) {
            log.info("Plantões encerrados por inatividade: {}", closed);
        }
        return closed;
    }

    /**
     * Plantão aberto e vivo do operador, ou {@link ShiftNotFoundException}.
     *
     * <p>O filtro do lease é aplicado na consulta, não por mutação: um plantão
     * vencido responde 404 como qualquer plantão inexistente, e o carimbo de
     * {@code ended_at}/{@code ended_reason} fica com o job — lançar exceção
     * dentro da transação reverteria justamente a marcação de encerramento.
     */
    private ShiftSession requireCurrent(UUID userId) {
        return shiftSessions
                .findByUserIdAndEndedAtIsNullAndLastSeenAtGreaterThanEqual(
                        userId, Instant.now().minus(idleTimeout))
                .orElseThrow(ShiftNotFoundException::new);
    }

    /**
     * Expiração preguiçosa: o plantão vale enquanto o último sinal de vida
     * estiver dentro de {@code shift-idle-timeout}. Aplicada nas leituras e na
     * elegibilidade do SOS, garante que um app fechado à força nunca continue
     * recebendo chamados, mesmo antes do job de carimbo passar.
     *
     * @return {@code true} quando o plantão foi encerrado agora
     */
    private boolean expireIfIdle(ShiftSession shift, Instant now) {
        Instant lastSeen = shift.getLastSeenAt();
        if (lastSeen == null || !lastSeen.isBefore(now.minus(idleTimeout))) {
            return false;
        }
        shift.end(lastSeen, ShiftEndReason.IDLE_TIMEOUT);
        log.info("Plantão encerrado por inatividade: {}", shift.getId());
        return true;
    }

    private void requireActiveTarget(UUID postId, UUID mobileBaseId) {        String sql = postId != null
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
