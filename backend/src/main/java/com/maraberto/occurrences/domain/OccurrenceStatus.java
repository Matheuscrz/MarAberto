package com.maraberto.occurrences.domain;

/**
 * Ciclo de vida da ocorrência. Ocorrência nunca é apagada: encerra com
 * desfecho (RN11).
 */
public enum OccurrenceStatus {
    /** Registrada e ainda sem atendimento iniciado (registro após o fato). */
    OPEN,
    /** Atendimento em curso. Ocorrência de SOS nasce neste estado. */
    IN_PROGRESS,
    CLOSED,
    CANCELLED,
    FALSE_ALARM
}
