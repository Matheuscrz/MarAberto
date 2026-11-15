package com.maraberto.emergency.application;

import com.maraberto.emergency.domain.EmergencyCall;
import com.maraberto.emergency.domain.EmergencyCallStatus;
import com.maraberto.emergency.infra.EmergencyCallRepository;
import com.maraberto.operations.api.EligibleResponder;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gravação transacional de um SOS novo.
 *
 * <p>Vive em um bean próprio para ser chamado de dentro de uma transação
 * separada: a violação de unicidade de {@code client_request_id} precisa
 * estourar no commit desta transação (e não abortar a transação de quem
 * tentou criar), para que o chamador consiga devolver o chamado que já existe.
 */
@Component
public class EmergencyCallRegistrar {

    private static final Logger log = LoggerFactory.getLogger(EmergencyCallRegistrar.class);

    private final EmergencyCallRepository callRepository;
    private final DispatchRecipientResolver recipientResolver;
    private final SosDispatchNotifier notifier;
    private final SosEscalationPolicy escalationPolicy;

    public EmergencyCallRegistrar(
            EmergencyCallRepository callRepository,
            DispatchRecipientResolver recipientResolver,
            SosDispatchNotifier notifier,
            SosEscalationPolicy escalationPolicy) {
        this.callRepository = callRepository;
        this.recipientResolver = recipientResolver;
        this.notifier = notifier;
        this.escalationPolicy = escalationPolicy;
    }

    @Transactional
    public EmergencyCall register(CreateEmergencyCallService.CreateEmergencyCallCommand command) {
        EmergencyCall call = new EmergencyCall();
        call.setClientRequestId(command.clientRequestId());
        call.setLatitude(command.latitude());
        call.setLongitude(command.longitude());
        call.setAccuracyMeters(command.accuracyMeters());
        call.setStatus(EmergencyCallStatus.DISPATCHED);
        // O flush é imediato para que a corrida de dois pedidos iguais apareça
        // aqui, ainda dentro do limite transacional do Spring.
        EmergencyCall saved = callRepository.saveAndFlush(call);

        Instant occurredAt = Instant.now();
        List<EligibleResponder> recipients = recipientResolver.resolve(
                command.latitude(), command.longitude());
        if (recipients.isEmpty()) {
            // Sem plantão aberto e sem coordenação cadastrada: o chamado existe,
            // mas não há a quem notificar. O console reconcilia por HTTP.
            log.warn("SOS {} registrado sem destinatário: nenhum plantão aberto nem coordenação ativa",
                    saved.getId());
        }
        notifier.notify(saved.getId(), recipients, 1, occurredAt);

        // Se ninguém assumir, a escada abre o primeiro anel. Sem anéis
        // configurados o chamado fica só com a coordenação (RN03).
        if (escalationPolicy.enabled()) {
            saved.setNextEscalationAt(escalationPolicy.deadlineFrom(occurredAt));
        }
        return saved;
    }
}
