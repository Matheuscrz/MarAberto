package com.maraberto.emergency.application;

import com.maraberto.emergency.domain.EmergencyCall;
import com.maraberto.emergency.domain.EmergencyCallStatus;
import com.maraberto.emergency.infra.EmergencyCallRepository;
import com.maraberto.operations.api.EligibleResponder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p>O chamado já nasce com o prazo do primeiro anel de escalonamento. Se
 * ninguém assumir dentro dele, {@link SosEscalationService} amplia o raio — o
 * despacho inicial continua sendo o do raio de cobertura do posto/base.
 */
@Service
public class CreateEmergencyCallService {

    private static final Logger log = LoggerFactory.getLogger(CreateEmergencyCallService.class);

    private final EmergencyCallRepository callRepository;
    private final DispatchRecipientResolver recipientResolver;
    private final SosDispatchNotifier notifier;
    private final SosEscalationPolicy escalationPolicy;

    public CreateEmergencyCallService(
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
        notifier.notify(saved.getId(), recipients, 1, occurredAt);

        // Se ninguém assumir, a escada abre o primeiro anel. Sem anéis
        // configurados o chamado fica só com a coordenação (RN03).
        if (escalationPolicy.enabled()) {
            saved.setNextEscalationAt(escalationPolicy.deadlineFrom(occurredAt));
        }
        return saved;
    }

    @Schema(name = "CreateEmergencyCallCommand", description = "Comando interno para criação idempotente de um SOS")
    public record CreateEmergencyCallCommand(
            String clientRequestId,
            double latitude,
            double longitude,
            Double accuracyMeters) {
    }
}
