package com.maraberto.occurrences.domain;

import com.maraberto.emergency.api.SosCallOutcome;

/**
 * Desfecho do atendimento (RF28/RN11). {@link #REFERRED} encerra a ocorrência
 * como atendida, mas registra o encaminhamento ao serviço de saúde.
 */
public enum OccurrenceOutcome {
    RESOLVED,
    REFERRED,
    FALSE_ALARM,
    CANCELLED;

    /** Situação final da ficha correspondente ao desfecho. */
    public OccurrenceStatus status() {
        return switch (this) {
            case RESOLVED, REFERRED -> OccurrenceStatus.CLOSED;
            case FALSE_ALARM -> OccurrenceStatus.FALSE_ALARM;
            case CANCELLED -> OccurrenceStatus.CANCELLED;
        };
    }

    /** Desfecho do chamado do SOS que originou a ocorrência. */
    public SosCallOutcome sosOutcome() {
        return switch (this) {
            case RESOLVED, REFERRED -> SosCallOutcome.RESOLVED;
            case FALSE_ALARM -> SosCallOutcome.FALSE_ALARM;
            case CANCELLED -> SosCallOutcome.CANCELLED;
        };
    }

    /** Atendimento sem registro obrigatório no CIOSP. */
    public boolean requiresCiosp() {
        return this != FALSE_ALARM && this != CANCELLED;
    }
}
