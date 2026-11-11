package com.maraberto.emergency.application;

import com.maraberto.emergency.domain.CallAlreadyAcknowledgedException;
import com.maraberto.emergency.domain.DispatchNotAssignedException;
import com.maraberto.emergency.domain.EmergencyCallStatus;
import com.maraberto.emergency.domain.EmergencyDispatch;
import com.maraberto.emergency.domain.EmergencyDispatchStatus;
import com.maraberto.emergency.infra.EmergencyCallRepository;
import com.maraberto.emergency.infra.EmergencyDispatchRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações do console sobre um SOS já despachado.
 *
 * <p>O ack é um compare-and-set no banco: o primeiro operador que encontra o
 * chamado em {@code DISPATCHED} vence; os demais têm o despacho cancelado. A
 * lista de chamados abertos é a reconciliação por HTTP usada quando o WebSocket
 * perde um frame.
 */
@Service
public class DispatchService {

    private static final List<EmergencyDispatchStatus> OPEN_DISPATCH_STATUSES =
            List.of(EmergencyDispatchStatus.PENDING, EmergencyDispatchStatus.NOTIFIED);

    private static final String OPEN_CALLS_SQL = """
            SELECT c.id AS call_id, c.status, c.latitude, c.longitude, c.accuracy_meters,
                   c.created_at, d.id AS dispatch_id, d.round, d.distance_m,
                   d.position_stale
            FROM emergency_calls c
            JOIN emergency_dispatches d ON d.emergency_call_id = c.id
            WHERE c.status = 'DISPATCHED'
              AND d.recipient_user_id = :userId
              AND d.status IN ('PENDING', 'NOTIFIED')
            ORDER BY c.created_at
            """;

    /**
     * Chamados que o operador já assumiu e ainda estão em atendimento. É o que
     * mantém o atendimento na mesa (com a rota até o banhista) depois do ack:
     * assumir não pode fazer o chamado desaparecer, porque é dele que sai a
     * ocorrência operacional (ADR-013).
     *
     * <p>A distância do despacho é a da abertura do chamado. Depois do ack o
     * guarda-vidas se desloca, então a ficha mostra a distância <b>atual</b>,
     * medida da última posição do plantão (que o console renova) até o ponto
     * informado pelo banhista — é o que sustenta a decisão de ir a pé ou de
     * veículo. Sem posição de plantão, a distância da abertura continua sendo
     * o fallback honesto.
     */
    private static final String ASSIGNED_CALLS_SQL = """
            SELECT c.id AS call_id, c.status, c.latitude, c.longitude, c.accuracy_meters,
                   c.created_at, c.acknowledged_at, d.id AS dispatch_id, d.round,
                   d.distance_m, d.position_stale,
                   sh.last_position_at AS current_position_at,
                   CASE WHEN sh.last_position IS NULL THEN NULL
                        ELSE ST_Distance(sh.last_position, c.location)
                   END AS current_distance_m
            FROM emergency_calls c
            JOIN emergency_dispatches d ON d.emergency_call_id = c.id
            LEFT JOIN shift_sessions sh
                   ON sh.user_id = :userId AND sh.ended_at IS NULL
            WHERE c.status IN ('ACKNOWLEDGED', 'EN_ROUTE', 'ON_SCENE')
              AND d.recipient_user_id = :userId
              AND d.status = 'ACKNOWLEDGED'
            ORDER BY c.acknowledged_at
            """;

    private final EmergencyCallRepository callRepository;
    private final EmergencyDispatchRepository dispatchRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration positionStaleAfter;

    public DispatchService(
            EmergencyCallRepository callRepository,
            EmergencyDispatchRepository dispatchRepository,
            NamedParameterJdbcTemplate jdbc,
            ApplicationEventPublisher eventPublisher,
            @Value("${maraberto.dispatch.position-stale-after:PT30M}") Duration positionStaleAfter) {
        this.callRepository = callRepository;
        this.dispatchRepository = dispatchRepository;
        this.jdbc = jdbc;
        this.eventPublisher = eventPublisher;
        this.positionStaleAfter = positionStaleAfter;
    }

    @Transactional(readOnly = true)
    public List<OpenCall> openCalls(UUID userId) {
        return jdbc.query(OPEN_CALLS_SQL,
                new MapSqlParameterSource("userId", userId),
                (resultSet, rowNumber) -> new OpenCall(
                        resultSet.getObject("call_id", UUID.class),
                        resultSet.getString("status"),
                        resultSet.getDouble("latitude"),
                        resultSet.getDouble("longitude"),
                        resultSet.getObject("accuracy_meters", Double.class),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getObject("dispatch_id", UUID.class),
                        resultSet.getInt("round"),
                        resultSet.getBigDecimal("distance_m"),
                        resultSet.getBoolean("position_stale")));
    }

    @Transactional(readOnly = true)
    public List<AssignedCall> assignedCalls(UUID userId) {
        Instant staleBefore = Instant.now().minus(positionStaleAfter);
        return jdbc.query(ASSIGNED_CALLS_SQL,
                new MapSqlParameterSource("userId", userId),
                (resultSet, rowNumber) -> {
                    var positionTimestamp = resultSet.getTimestamp("current_position_at");
                    Instant currentPositionAt = positionTimestamp == null
                            ? null
                            : positionTimestamp.toInstant();
                    return new AssignedCall(
                            resultSet.getObject("call_id", UUID.class),
                            resultSet.getString("status"),
                            resultSet.getDouble("latitude"),
                            resultSet.getDouble("longitude"),
                            resultSet.getObject("accuracy_meters", Double.class),
                            resultSet.getTimestamp("created_at").toInstant(),
                            resultSet.getTimestamp("acknowledged_at").toInstant(),
                            resultSet.getObject("dispatch_id", UUID.class),
                            resultSet.getInt("round"),
                            resultSet.getBigDecimal("distance_m"),
                            resultSet.getBoolean("position_stale"),
                            resultSet.getBigDecimal("current_distance_m"),
                            currentPositionAt,
                            currentPositionAt == null || currentPositionAt.isBefore(staleBefore));
                });
    }

    @Transactional
    public AckResult acknowledge(UUID callId, UUID userId) {
        Instant now = Instant.now();
        EmergencyDispatch mine = dispatchRepository
                .findFirstByEmergencyCallIdAndRecipientUserIdOrderByCreatedAtDesc(callId, userId)
                .orElseThrow(DispatchNotAssignedException::new);

        // Recebeu o alerta, mas outro operador assumiu antes: é conflito, não
        // falta de atribuição. Quem nunca foi despachado segue em 403.
        if (mine.getStatus() == EmergencyDispatchStatus.CANCELLED) {
            throw new CallAlreadyAcknowledgedException();
        }

        int updated = callRepository.acknowledge(callId, mine.getRecipientPostId(), now);
        if (updated == 0) {
            throw new CallAlreadyAcknowledgedException();
        }

        mine.acknowledge(now);

        List<SosAcknowledged.CancelledRecipient> cancelled = dispatchRepository
                .findByEmergencyCallIdAndStatusIn(callId, OPEN_DISPATCH_STATUSES)
                .stream()
                .filter(dispatch -> !dispatch.getId().equals(mine.getId()))
                .map(dispatch -> new SosAcknowledged.CancelledRecipient(
                        dispatch.getRecipientUserId(),
                        dispatch.getId(),
                        dispatch.getRound()))
                .filter(recipient -> recipient.userId() != null)
                .toList();

        dispatchRepository.cancelOthers(callId, mine.getId(), now);
        eventPublisher.publishEvent(new SosAcknowledged(callId, now, cancelled));

        return new AckResult(
                callId,
                EmergencyCallStatus.ACKNOWLEDGED.name(),
                mine.getId(),
                mine.getRecipientPostId(),
                now);
    }

    public record OpenCall(
            UUID callId,
            String status,
            double latitude,
            double longitude,
            Double accuracyMeters,
            Instant createdAt,
            UUID dispatchId,
            int round,
            BigDecimal distanceMeters,
            boolean positionStale) {
    }

    /**
     * Chamado assumido pelo operador, ainda em atendimento. Guarda o horário do
     * ack para o console mostrar desde quando o atendimento está aberto e para a
     * ocorrência operacional nascer com o instante correto.
     */
    public record AssignedCall(
            UUID callId,
            String status,
            double latitude,
            double longitude,
            Double accuracyMeters,
            Instant createdAt,
            Instant acknowledgedAt,
            UUID dispatchId,
            int round,
            BigDecimal distanceMeters,
            boolean positionStale,
            BigDecimal currentDistanceMeters,
            Instant currentPositionAt,
            boolean currentPositionStale) {
    }

    public record AckResult(
            UUID callId,
            String status,
            UUID dispatchId,
            UUID assignedPostId,
            Instant acknowledgedAt) {
    }
}
