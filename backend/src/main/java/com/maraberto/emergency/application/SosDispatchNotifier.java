package com.maraberto.emergency.application;

import com.maraberto.emergency.api.EmergencyCallDispatched;
import com.maraberto.emergency.domain.EmergencyDispatch;
import com.maraberto.emergency.domain.EmergencyDispatchStatus;
import com.maraberto.emergency.domain.EmergencyOutboxEvent;
import com.maraberto.emergency.infra.EmergencyDispatchRepository;
import com.maraberto.emergency.infra.EmergencyOutboxEventRepository;
import com.maraberto.operations.api.EligibleResponder;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Materializa o despacho de um SOS: uma linha em {@code emergency_dispatches}
 * por destinatário e um evento cifrado na outbox, na transação corrente.
 *
 * <p>É o ponto comum entre o despacho inicial (round 1, dentro da transação do
 * chamado) e a abertura de um anel de escalonamento. Manter isso em um único
 * lugar garante que os dois caminhos gravem a mesma prova de notificação — o
 * que o console reconcilia por HTTP quando um frame se perde (ADR-018).
 */
@Component
public class SosDispatchNotifier {

    private final EmergencyDispatchRepository dispatchRepository;
    private final EmergencyOutboxEventRepository outboxRepository;
    private final EmergencyOutboxSerializer outboxSerializer;
    private final SensitiveDataProtector sensitiveDataProtector;
    private final ApplicationEventPublisher eventPublisher;

    public SosDispatchNotifier(
            EmergencyDispatchRepository dispatchRepository,
            EmergencyOutboxEventRepository outboxRepository,
            EmergencyOutboxSerializer outboxSerializer,
            SensitiveDataProtector sensitiveDataProtector,
            ApplicationEventPublisher eventPublisher) {
        this.dispatchRepository = dispatchRepository;
        this.outboxRepository = outboxRepository;
        this.outboxSerializer = outboxSerializer;
        this.sensitiveDataProtector = sensitiveDataProtector;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @return quantos destinatários receberam o alerta nesta chamada
     */
    public int notify(
            UUID callId,
            List<EligibleResponder> recipients,
            int round,
            Instant occurredAt) {
        recipients.forEach(recipient -> notifyOne(callId, recipient, round, occurredAt));
        return recipients.size();
    }

    private void notifyOne(
            UUID callId, EligibleResponder recipient, int round, Instant occurredAt) {
        EmergencyDispatch dispatch = new EmergencyDispatch();
        dispatch.setEmergencyCallId(callId);
        dispatch.setRound((short) round);
        dispatch.setRecipientUserId(recipient.userId());
        dispatch.setRecipientPostId(recipient.postId());
        dispatch.setRecipientBaseId(recipient.baseId());
        dispatch.setDistanceMeters(toDistance(recipient.distanceMeters()));
        dispatch.setPositionStale(recipient.positionStale());
        dispatch.setStatus(EmergencyDispatchStatus.PENDING);
        EmergencyDispatch saved = dispatchRepository.save(dispatch);

        EmergencyCallDispatched event = new EmergencyCallDispatched(
                callId,
                saved.getId(),
                recipient.userId(),
                recipient.postId(),
                recipient.baseId(),
                round,
                occurredAt);

        EmergencyOutboxEvent outboxEvent = new EmergencyOutboxEvent();
        outboxEvent.setAggregateId(callId);
        outboxEvent.setEventType(EmergencyCallDispatched.class.getName());
        outboxEvent.setPayload(sensitiveDataProtector.encrypt(
                outboxSerializer.toJson(event), SensitiveDataProtector.AAD_EMERGENCY_OUTBOX));
        outboxEvent.setStatus(EmergencyOutboxEvent.OutboxStatus.PENDING);
        outboxRepository.save(outboxEvent);

        // Acorda o publicador depois do commit. Se a publicação falhar, o sweep
        // periódico reenvia — o chamado já está garantido no banco.
        eventPublisher.publishEvent(new EmergencyOutboxEventQueued(outboxEvent.getId()));
    }

    private static BigDecimal toDistance(Double distanceMeters) {
        if (distanceMeters == null) {
            return null;
        }
        return BigDecimal.valueOf(distanceMeters).setScale(1, RoundingMode.HALF_UP);
    }
}
