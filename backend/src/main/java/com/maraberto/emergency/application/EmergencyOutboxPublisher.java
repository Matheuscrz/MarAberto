package com.maraberto.emergency.application;

import com.maraberto.emergency.api.EmergencyCallDispatched;
import com.maraberto.emergency.api.EmergencyCallNotification;
import com.maraberto.realtime.api.SosNotifierPort;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publicador da outbox do SOS.
 *
 * <p>A publicação acontece <strong>fora</strong> da transação que reivindica as
 * linhas: reivindicar (transação curta) → publicar → marcar o resultado em outra
 * transação curta. Um broker lento não segura locks de linhas da outbox. A
 * reivindicação usa um <em>lease</em> ({@code claimed_at}), então uma instância
 * que cai no meio do caminho tem o evento reassumido pelo sweep.
 *
 * <p>A entrega é <em>at-least-once</em>: o console precisa descartar duplicata
 * por {@code dispatchId}. Cada evento carrega a lista de destinatários já
 * resolvida pelo banco (ADR-018) — o publicador não decide nada.
 */
@Service
public class EmergencyOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(EmergencyOutboxPublisher.class);
    private static final int MAX_ERROR_LENGTH = 200;

    private static final String CLAIM_BATCH_SQL = """
            WITH pending AS (
                SELECT id FROM emergency_outbox_events
                WHERE status = 'PENDING'
                  AND (claimed_at IS NULL
                       OR claimed_at < NOW() - make_interval(secs => :leaseSeconds))
                ORDER BY occurred_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE emergency_outbox_events e
            SET claimed_at = NOW()
            FROM pending
            WHERE e.id = pending.id
            RETURNING e.id, e.event_type, e.payload, e.attempts
            """;

    private static final String CLAIM_ONE_SQL = """
            WITH pending AS (
                SELECT id FROM emergency_outbox_events
                WHERE id = :id
                  AND status = 'PENDING'
                  AND (claimed_at IS NULL
                       OR claimed_at < NOW() - make_interval(secs => :leaseSeconds))
                FOR UPDATE SKIP LOCKED
            )
            UPDATE emergency_outbox_events e
            SET claimed_at = NOW()
            FROM pending
            WHERE e.id = pending.id
            RETURNING e.id, e.event_type, e.payload, e.attempts
            """;

    private static final String RECORD_SUCCESS_SQL = """
            UPDATE emergency_outbox_events
            SET status = 'PUBLISHED', published_at = :publishedAt,
                last_error = NULL, claimed_at = NULL
            WHERE id = :id
            """;

    private static final String RECORD_FAILURE_SQL = """
            UPDATE emergency_outbox_events
            SET status = :status, attempts = :attempts,
                last_error = :lastError, claimed_at = NULL
            WHERE id = :id
            """;

    private static final String MARK_DISPATCH_NOTIFIED_SQL = """
            UPDATE emergency_dispatches
            SET status = 'NOTIFIED', notified_at = :notifiedAt, updated_at = :notifiedAt
            WHERE id = :id AND status = 'PENDING'
            """;

    private static final String DELIVERY_PENDING_SQL = """
            UPDATE emergency_dispatches
            SET status = 'PENDING', notified_at = NULL
            WHERE id = :id AND status = 'NOTIFIED'
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final SosNotifierPort sosNotifier;
    private final EmergencyOutboxSerializer serializer;
    private final SensitiveDataProtector sensitiveDataProtector;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final int maxAttempts;
    private final int leaseSeconds;

    public EmergencyOutboxPublisher(
            NamedParameterJdbcTemplate jdbc,
            SosNotifierPort sosNotifier,
            EmergencyOutboxSerializer serializer,
            SensitiveDataProtector sensitiveDataProtector,
            PlatformTransactionManager transactionManager,
            @Value("${maraberto.emergency.outbox.batch-size:50}") int batchSize,
            @Value("${maraberto.emergency.outbox.max-attempts:10}") int maxAttempts,
            @Value("${maraberto.emergency.outbox.claim-lease:PT30S}") Duration claimLease) {
        this.jdbc = jdbc;
        this.sosNotifier = sosNotifier;
        this.serializer = serializer;
        this.sensitiveDataProtector = sensitiveDataProtector;
        // REQUIRES_NEW é obrigatório: o listener AFTER_COMMIT ainda tem os
        // recursos da transação original ligados à thread.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.leaseSeconds = Math.max(1, Math.toIntExact(claimLease.toSeconds()));
    }

    /**
     * Publica um lote de eventos pendentes. Retorna quantos foram reivindicados.
     */
    public int publishPending() {
        List<PendingEvent> events = transactionTemplate.execute(
                status -> claim(CLAIM_BATCH_SQL, new MapSqlParameterSource()
                        .addValue("batchSize", batchSize)
                        .addValue("leaseSeconds", leaseSeconds)));
        if (events == null || events.isEmpty()) {
            return 0;
        }
        events.forEach(this::publishOutsideTransaction);
        return events.size();
    }

    /**
     * Reação imediata ao commit do SOS. Roda fora da transação original, então
     * abre uma transação própria para reivindicar e outra para marcar o
     * resultado.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventQueued(EmergencyOutboxEventQueued queued) {
        try {
            List<PendingEvent> claimed = transactionTemplate.execute(
                    status -> claim(CLAIM_ONE_SQL, new MapSqlParameterSource()
                            .addValue("id", queued.outboxEventId())
                            .addValue("leaseSeconds", leaseSeconds)));
            if (claimed != null) {
                claimed.forEach(this::publishOutsideTransaction);
            }
        } catch (RuntimeException exception) {
            // O evento continua PENDING até o lease expirar; o sweep repete.
            // Nada de PII no log.
            log.warn("Publicação imediata do evento {} falhou; o sweep da outbox vai repetir: {}",
                    queued.outboxEventId(), exception.getClass().getSimpleName());
        }
    }

    private List<PendingEvent> claim(String sql, MapSqlParameterSource parameters) {
        return jdbc.query(sql, parameters, (resultSet, rowNumber) -> new PendingEvent(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getObject("payload", String.class),
                resultSet.getInt("attempts")));
    }

    private void publishOutsideTransaction(PendingEvent event) {
        Delivery delivery;
        try {
            delivery = toDelivery(event);
        } catch (RuntimeException exception) {
            recordFailure(event, null, exception);
            return;
        }

        try {
            sosNotifier.send(delivery.recipientUserId(), delivery.notification());
            transactionTemplate.executeWithoutResult(status -> recordSuccess(delivery));
        } catch (RuntimeException exception) {
            recordFailure(event, delivery, exception);
        }
    }

    private void recordSuccess(Delivery delivery) {
        Instant now = Instant.now();
        // O driver não infere Instant em parâmetro de JDBC; OffsetDateTime vira
        // TIMESTAMPTZ sem depender do timezone da sessão.
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        jdbc.update(RECORD_SUCCESS_SQL, new MapSqlParameterSource()
                .addValue("id", delivery.eventId())
                .addValue("publishedAt", timestamp));
        jdbc.update(MARK_DISPATCH_NOTIFIED_SQL, new MapSqlParameterSource()
                .addValue("id", delivery.notification().dispatchId())
                .addValue("notifiedAt", timestamp));
    }

    private void recordFailure(PendingEvent event, Delivery delivery, RuntimeException exception) {
        int attempts = event.attempts() + 1;
        String nextStatus = attempts >= maxAttempts ? "FAILED" : "PENDING";
        log.warn("Falha ao publicar o evento {} da outbox do SOS (tentativa {}/{}): {}",
                event.id(), attempts, maxAttempts, exception.getClass().getSimpleName());
        try {
            transactionTemplate.executeWithoutResult(transactionStatus -> {
                jdbc.update(RECORD_FAILURE_SQL, new MapSqlParameterSource()
                        .addValue("id", event.id())
                        .addValue("status", nextStatus)
                        .addValue("attempts", attempts)
                        .addValue("lastError", truncate(exception.getClass().getSimpleName())));
                if (delivery != null) {
                    // O aviso não saiu: o despacho volta a PENDING e pode ser
                    // reenviado pelo sweep sem depender do estado de NOTIFIED.
                    jdbc.update(DELIVERY_PENDING_SQL, new MapSqlParameterSource()
                            .addValue("id", delivery.notification().dispatchId()));
                }
            });
        } catch (RuntimeException recordingFailure) {
            log.warn("Não foi possível registrar a falha do evento {} da outbox: {}",
                    event.id(), recordingFailure.getClass().getSimpleName());
        }
    }

    private Delivery toDelivery(PendingEvent event) {
        if (!EmergencyCallDispatched.class.getName().equals(event.eventType())) {
            throw new IllegalStateException("Tipo de evento não suportado pela outbox do SOS");
        }
        String json = sensitiveDataProtector.decrypt(
                event.payload(), SensitiveDataProtector.AAD_EMERGENCY_OUTBOX);
        EmergencyCallDispatched dispatched = serializer.fromJson(json, EmergencyCallDispatched.class);
        EmergencyCallNotification notification = new EmergencyCallNotification(
                dispatched.callId(),
                dispatched.dispatchId(),
                dispatched.round(),
                "DISPATCHED",
                dispatched.occurredAt());
        return new Delivery(event.id(), dispatched.recipientUserId(), notification);
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    private record PendingEvent(UUID id, String eventType, String payload, int attempts) {
    }

    private record Delivery(UUID eventId, UUID recipientUserId, EmergencyCallNotification notification) {
    }
}
