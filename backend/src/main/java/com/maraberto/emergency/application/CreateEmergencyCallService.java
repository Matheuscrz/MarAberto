package com.maraberto.emergency.application;

import com.maraberto.emergency.api.EmergencyCallDispatched;
import com.maraberto.emergency.domain.EmergencyCall;
import com.maraberto.emergency.domain.EmergencyCallStatus;
import com.maraberto.emergency.domain.EmergencyDispatch;
import com.maraberto.emergency.domain.EmergencyDispatchStatus;
import com.maraberto.emergency.domain.EmergencyOutboxEvent;
import com.maraberto.emergency.infra.EmergencyCallRepository;
import com.maraberto.emergency.infra.EmergencyDispatchRepository;
import com.maraberto.emergency.infra.EmergencyOutboxEventRepository;
import com.maraberto.operations.api.EligibleResponder;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criação do SOS: grava o chamado, resolve os destinatários e grava um
 * despacho por destinatário na mesma transação (ADR-018).
 *
 * <p>Nunca falhar por causa da seleção: se a consulta espacial der erro ou não
 * houver plantão aberto, o chamado continua salvo e a coordenação é acionada
 * (RN03). A seleção roda em transação separada justamente para que um erro nela
 * não aborte a transação do SOS.
 */
@Service
public class CreateEmergencyCallService {

    private static final Logger log = LoggerFactory.getLogger(CreateEmergencyCallService.class);

    private final EmergencyCallRepository callRepository;
    private final EmergencyDispatchRepository dispatchRepository;
    private final EmergencyOutboxEventRepository outboxRepository;
    private final DispatchRecipientResolver recipientResolver;
    private final EmergencyOutboxSerializer outboxSerializer;
    private final SensitiveDataProtector sensitiveDataProtector;
    private final ApplicationEventPublisher eventPublisher;

    public CreateEmergencyCallService(
            EmergencyCallRepository callRepository,
            EmergencyDispatchRepository dispatchRepository,
            EmergencyOutboxEventRepository outboxRepository,
            DispatchRecipientResolver recipientResolver,
            EmergencyOutboxSerializer outboxSerializer,
            SensitiveDataProtector sensitiveDataProtector,
            ApplicationEventPublisher eventPublisher) {
        this.callRepository = callRepository;
        this.dispatchRepository = dispatchRepository;
        this.outboxRepository = outboxRepository;
        this.recipientResolver = recipientResolver;
        this.outboxSerializer = outboxSerializer;
        this.sensitiveDataProtector = sensitiveDataProtector;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public EmergencyCall create(CreateEmergencyCallCommand command) {
        return callRepository.findByClientRequestId(command.clientRequestId())
                .orElseGet(() -> createNew(command));
    }

    private EmergencyCall createNew(CreateEmergencyCallCommand command) {
        EmergencyCall call = new EmergencyCall();
        call.setClientRequestId(command.clientRequestId());
        call.setLatitude(command.latitude());
        call.setLongitude(command.longitude());
        call.setAccuracyMeters(command.accuracyMeters());
        call.setStatus(EmergencyCallStatus.DISPATCHED);
        EmergencyCall saved = callRepository.save(call);

        Instant occurredAt = Instant.now();
        List<EligibleResponder> recipients = recipientResolver.resolve(
                command.latitude(), command.longitude());
        if (recipients.isEmpty()) {
            // Sem plantão aberto e sem coordenação cadastrada: o chamado existe,
            // mas não há a quem notificar. O console reconcilia por HTTP.
            log.warn("SOS {} registrado sem destinatário: nenhum plantão aberto nem coordenação ativa",
                    saved.getId());
        }
        recipients.forEach(recipient -> dispatchTo(saved, recipient, occurredAt));

        return saved;
    }

    private void dispatchTo(EmergencyCall call, EligibleResponder recipient, Instant occurredAt) {
        EmergencyDispatch dispatch = new EmergencyDispatch();
        dispatch.setEmergencyCallId(call.getId());
        dispatch.setRound((short) 1);
        dispatch.setRecipientUserId(recipient.userId());
        dispatch.setRecipientPostId(recipient.postId());
        dispatch.setRecipientBaseId(recipient.baseId());
        dispatch.setDistanceMeters(toDistance(recipient.distanceMeters()));
        dispatch.setPositionStale(recipient.positionStale());
        dispatch.setStatus(EmergencyDispatchStatus.PENDING);
        dispatch = dispatchRepository.save(dispatch);

        EmergencyCallDispatched event = new EmergencyCallDispatched(
                call.getId(),
                dispatch.getId(),
                recipient.userId(),
                recipient.postId(),
                recipient.baseId(),
                1,
                occurredAt);

        EmergencyOutboxEvent outboxEvent = new EmergencyOutboxEvent();
        outboxEvent.setAggregateId(call.getId());
        outboxEvent.setEventType(EmergencyCallDispatched.class.getName());
        outboxEvent.setPayload(sensitiveDataProtector.encrypt(
                outboxSerializer.toJson(event), SensitiveDataProtector.AAD_EMERGENCY_OUTBOX));
        outboxEvent.setStatus(EmergencyOutboxEvent.OutboxStatus.PENDING);
        outboxRepository.save(outboxEvent);

        // Acorda o publicador depois do commit. Se a publicação falhar, o sweep
        // periódico reenvia — a chamada do SOS já está garantida no banco.
        eventPublisher.publishEvent(new EmergencyOutboxEventQueued(outboxEvent.getId()));
    }

    private static BigDecimal toDistance(Double distanceMeters) {
        if (distanceMeters == null) {
            return null;
        }
        return BigDecimal.valueOf(distanceMeters).setScale(1, RoundingMode.HALF_UP);
    }

    @Schema(name = "CreateEmergencyCallCommand", description = "Comando interno para criação idempotente de um SOS")
    public record CreateEmergencyCallCommand(
            String clientRequestId,
            double latitude,
            double longitude,
            Double accuracyMeters) {
    }
}
