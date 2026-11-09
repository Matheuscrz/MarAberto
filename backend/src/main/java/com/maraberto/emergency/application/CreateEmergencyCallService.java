package com.maraberto.emergency.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.emergency.api.EmergencyCallCreated;
import com.maraberto.emergency.domain.EmergencyCall;
import com.maraberto.emergency.domain.EmergencyCallStatus;
import com.maraberto.emergency.domain.EmergencyDispatch;
import com.maraberto.emergency.domain.EmergencyDispatchStatus;
import com.maraberto.emergency.domain.EmergencyOutboxEvent;
import com.maraberto.emergency.infra.EmergencyCallRepository;
import com.maraberto.emergency.infra.EmergencyDispatchRepository;
import com.maraberto.emergency.infra.EmergencyOutboxEventRepository;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateEmergencyCallService {

    private final EmergencyCallRepository callRepository;
    private final EmergencyDispatchRepository dispatchRepository;
    private final EmergencyOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final SensitiveDataProtector sensitiveDataProtector;
    private final ApplicationEventPublisher eventPublisher;

    public CreateEmergencyCallService(
            EmergencyCallRepository callRepository,
            EmergencyDispatchRepository dispatchRepository,
            EmergencyOutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            SensitiveDataProtector sensitiveDataProtector,
            ApplicationEventPublisher eventPublisher) {
        this.callRepository = callRepository;
        this.dispatchRepository = dispatchRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
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
        call = callRepository.save(call);

        EmergencyDispatch dispatch = new EmergencyDispatch();
        dispatch.setEmergencyCallId(call.getId());
        dispatch.setStatus(EmergencyDispatchStatus.PENDING);
        dispatchRepository.save(dispatch);

        Instant occurredAt = Instant.now();
        EmergencyCallCreated event = new EmergencyCallCreated(
                call.getId(),
                call.getClientRequestId(),
                call.getLatitude(),
                call.getLongitude(),
                call.getAccuracyMeters(),
                occurredAt);
        EmergencyOutboxEvent outboxEvent = new EmergencyOutboxEvent();
        outboxEvent.setAggregateId(call.getId());
        outboxEvent.setEventType(EmergencyCallCreated.class.getName());
        outboxEvent.setPayload(sensitiveDataProtector.encrypt(
                toJson(event), SensitiveDataProtector.AAD_EMERGENCY_OUTBOX));
        outboxEvent.setStatus(EmergencyOutboxEvent.OutboxStatus.PENDING);
        outboxRepository.save(outboxEvent);

        // Acorda o publicador depois do commit. Se a publicação falhar, o sweep
        // periódico reenvia — a chamada do SOS já está garantida no banco.
        eventPublisher.publishEvent(new EmergencyOutboxEventQueued(outboxEvent.getId()));

        return call;
    }

    private String toJson(EmergencyCallCreated event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar o evento de criação do SOS", exception);
        }
    }

    @Schema(name = "CreateEmergencyCallCommand", description = "Comando interno para criação idempotente de um SOS")
    public record CreateEmergencyCallCommand(
            String clientRequestId,
            double latitude,
            double longitude,
            Double accuracyMeters) {
    }
}
