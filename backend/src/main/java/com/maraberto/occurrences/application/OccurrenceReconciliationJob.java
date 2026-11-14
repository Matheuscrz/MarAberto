package com.maraberto.occurrences.application;

import com.maraberto.occurrences.infra.OccurrenceRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rede de segurança da abertura por evento (RF32/ADR-013).
 *
 * <p>O listener roda depois do commit, mas pode falhar (banco fora, deploy). O
 * job olha os chamados assumidos há mais de um minuto e abre o que faltar; a
 * idempotência por {@code emergency_call_id} garante que rodar de novo não
 * duplica nada.
 */
@Component
public class OccurrenceReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(OccurrenceReconciliationJob.class);
    private static final Duration GRACE = Duration.ofMinutes(1);

    private final OccurrenceRepository occurrenceRepository;

    public OccurrenceReconciliationJob(OccurrenceRepository occurrenceRepository) {
        this.occurrenceRepository = occurrenceRepository;
    }

    @Scheduled(
            fixedDelayString = "${maraberto.occurrences.reconciliation.delay:PT5M}",
            initialDelayString = "${maraberto.occurrences.reconciliation.initial-delay:PT1M}")
    public void reconcile() {
        try {
            int created = occurrenceRepository.openMissingAcknowledged(Instant.now().minus(GRACE));
            if (created > 0) {
                log.info("Reconciliação abriu {} ocorrência(s) de SOS sem ficha.", created);
            }
        } catch (RuntimeException cause) {
            // O job se repete: um erro de banco não pode matar o agendador.
            log.warn("Falha na reconciliação das ocorrências: {}", cause.toString());
        }
    }
}
