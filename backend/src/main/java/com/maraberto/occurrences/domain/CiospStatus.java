package com.maraberto.occurrences.domain;

/**
 * Pendência de transcrição para o CIOSP (RF34/RN14). O MarAberto não se
 * integra ao CIOSP: alguém informa o protocolo gerado no sistema do CBM.
 */
public enum CiospStatus {
    PENDING,
    REGISTERED,
    /** Atendimento sem registro obrigatório (falso alarme, cancelado). */
    NOT_REQUIRED
}
