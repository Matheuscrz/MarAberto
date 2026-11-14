package com.maraberto.occurrences.application;

import com.maraberto.emergency.api.SosAcknowledged;
import com.maraberto.occurrences.infra.OccurrenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Abre a ocorrência no instante em que o guarda-vidas assume o chamado
 * (ADR-013).
 *
 * <p>O {@code AFTER_COMMIT} mantém o SOS fora do caminho crítico: o ack já
 * respondeu ao operador e, se a criação da ficha falhar, o alerta continua
 * assumido. A falha vira log e o {@link OccurrenceReconciliationJob} abre a
 * ocorrência faltante na próxima rodada.
 */
@Component
public class SosOccurrenceListener {

    private static final Logger log = LoggerFactory.getLogger(SosOccurrenceListener.class);

    private final OccurrenceRepository occurrenceRepository;

    public SosOccurrenceListener(OccurrenceRepository occurrenceRepository) {
        this.occurrenceRepository = occurrenceRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSosAcknowledged(SosAcknowledged event) {
        try {
            occurrenceRepository.openFromAcknowledgedCall(event.callId());
        } catch (RuntimeException cause) {
            // A reconciliação é a rede de segurança: o atendimento não depende
            // desta escrita ter dado certo agora.
            log.warn("Falha ao abrir a ocorrência do chamado {}: {}",
                    event.callId(), cause.toString());
        }
    }
}
