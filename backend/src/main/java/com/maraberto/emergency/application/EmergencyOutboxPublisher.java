package com.maraberto.emergency.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.emergency.api.EmergencyCallCreated;
import com.maraberto.emergency.api.EmergencyCallNotification;
import com.maraberto.emergency.domain.EmergencyCallStatus;
import com.maraberto.emergency.domain.EmergencyOutboxEvent;
import com.maraberto.emergency.infra.EmergencyOutboxEventRepository;
import com.maraberto.realtime.api.RealtimeDestinations;
import com.maraberto.realtime.api.RealtimePublisherPort;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publicador da outbox do SOS.
 *
 * <p>Dois gatilhos usam o mesmo caminho de publicação:
 * <ol>
 *   <li>após o commit do SOS ({@link TransactionalEventListener}), para manter
 *       a latência baixa;</li>
 *   <li>um sweep periódico ({@link EmergencyOutboxSweeper}), que recupera
 *       eventos pendentes depois de uma queda ou de uma falha do broker.</li>
 * </ol>
 *
 * <p>Os eventos são reivindicados com {@code FOR UPDATE SKIP LOCKED}, o que
 * permite várias instâncias sem publicar o mesmo evento ao mesmo tempo. A
 * entrega é <em>at-least-once</em>: o console precisa ser idempotente por
 * {@code callId}, como já é o {@code clientRequestId} na criação do SOS.
 */
@Service
public class EmergencyOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(EmergencyOutboxPublisher.class);
    private static final int MAX_ERROR_LENGTH = 200;

    private final EmergencyOutboxEventRepository outboxRepository;
    private final RealtimePublisherPort realtimePublisher;
    private final ObjectMapper objectMapper;
    private final SensitiveDataProtector sensitiveDataProtector;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final int maxAttempts;

    public EmergencyOutboxPublisher(
            EmergencyOutboxEventRepository outboxRepository,
            RealtimePublisherPort realtimePublisher,
            ObjectMapper objectMapper,
            SensitiveDataProtector sensitiveDataProtector,
            PlatformTransactionManager transactionManager,
            @Value("${maraberto.emergency.outbox.batch-size:50}") int batchSize,
            @Value("${maraberto.emergency.outbox.max-attempts:10}") int maxAttempts) {
        this.outboxRepository = outboxRepository;
        this.realtimePublisher = realtimePublisher;
        this.objectMapper = objectMapper;
        this.sensitiveDataProtector = sensitiveDataProtector;
        // REQUIRES_NEW é obrigatório: o listener AFTER_COMMIT ainda tem os
        // recursos da transação original ligados à thread, e um REQUIRED
        // "entraria" na transação já commitada sem gravar nada.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Publica um lote de eventos pendentes. Retorna quantos foram reivindicados.
     */
    public int publishPending() {
        Integer claimed = transactionTemplate.execute(status -> {
            List<EmergencyOutboxEvent> events = outboxRepository.claimPendingBatch(batchSize);
            events.forEach(this::publishOne);
            return events.size();
        });
        return claimed == null ? 0 : claimed;
    }

    /**
     * Reação imediata ao commit do SOS. Roda fora da transação original, então
     * abre uma transação própria para atualizar o estado da outbox.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventQueued(EmergencyOutboxEventQueued queued) {
        publishQueuedEvent(queued.outboxEventId());
    }

    private void publishQueuedEvent(UUID outboxEventId) {
        try {
            transactionTemplate.executeWithoutResult(status -> outboxRepository
                    .findForUpdate(outboxEventId)
                    .ifPresent(this::publishOne));
        } catch (RuntimeException exception) {
            // O evento continua PENDING; o sweep tenta novamente. Nada de PII no log.
            log.warn("Publicação imediata do evento {} falhou; o sweep da outbox vai repetir: {}",
                    outboxEventId, exception.getClass().getSimpleName());
        }
    }

    private void publishOne(EmergencyOutboxEvent event) {
        try {
            realtimePublisher.publish(
                    RealtimeDestinations.OPERATIONAL_EMERGENCY_CALLS,
                    toNotification(event));
            event.setStatus(EmergencyOutboxEvent.OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setLastError(null);
        } catch (RuntimeException exception) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setLastError(truncate(exception.getClass().getSimpleName()));
            event.setStatus(attempts >= maxAttempts
                    ? EmergencyOutboxEvent.OutboxStatus.FAILED
                    : EmergencyOutboxEvent.OutboxStatus.PENDING);
            log.warn("Falha ao publicar o evento {} da outbox do SOS (tentativa {}/{}): {}",
                    event.getId(), attempts, maxAttempts, exception.getClass().getSimpleName());
        }
    }

    private EmergencyCallNotification toNotification(EmergencyOutboxEvent event) {
        if (!EmergencyCallCreated.class.getName().equals(event.getEventType())) {
            throw new IllegalStateException("Tipo de evento não suportado pela outbox do SOS");
        }

        String json = sensitiveDataProtector.decrypt(
                event.getPayload(), SensitiveDataProtector.AAD_EMERGENCY_OUTBOX);
        EmergencyCallCreated created = readEvent(json);

        // Redação deliberada: o tópico é aberto até a etapa de identidade.
        return new EmergencyCallNotification(
                created.callId(),
                EmergencyCallStatus.DISPATCHED.name(),
                created.occurredAt());
    }

    private EmergencyCallCreated readEvent(String json) {
        try {
            return objectMapper.readValue(json, EmergencyCallCreated.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Evento da outbox do SOS ilegível", exception);
        }
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
