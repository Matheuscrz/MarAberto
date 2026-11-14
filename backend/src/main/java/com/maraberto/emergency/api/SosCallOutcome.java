package com.maraberto.emergency.api;

/**
 * Desfecho do chamado do SOS, derivado do desfecho da ocorrência.
 */
public enum SosCallOutcome {
    RESOLVED,
    FALSE_ALARM,
    CANCELLED
}
