package com.maraberto.emergency.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Abre os anéis de escalonamento vencidos (ADR-018).
 *
 * <p>Intervalo curto de propósito: o anel é a resposta para "ninguém assumiu
 * ainda" e cada segundo conta. A consulta usa o índice parcial
 * {@code idx_emergency_calls_escalation} e devolve vazio quando não há chamado
 * pendente, então rodar a cada 10 s é barato.
 *
 * <p>O trabalho por chamado é uma transação curta de banco — o job não usa
 * virtual threads porque não há espera de I/O externo a paralelizar: a
 * concorrência é entre instâncias, resolvida por {@code FOR UPDATE SKIP LOCKED}.
 */
@Component
public class SosEscalationJob {

    private static final Logger log = LoggerFactory.getLogger(SosEscalationJob.class);

    private final SosEscalationService escalationService;

    public SosEscalationJob(SosEscalationService escalationService) {
        this.escalationService = escalationService;
    }

    @Scheduled(
            fixedDelayString = "${maraberto.dispatch.escalation.sweep-interval:PT10S}",
            initialDelayString = "${maraberto.dispatch.escalation.sweep-initial-delay:PT15S}")
    public void openDueRings() {
        try {
            escalationService.escalateDueCalls();
        } catch (RuntimeException cause) {
            // O job se repete: um erro de banco não pode matar o agendador.
            log.warn("Falha ao abrir os anéis de escalonamento do SOS: {}",
                    cause.getClass().getSimpleName());
        }
    }
}
