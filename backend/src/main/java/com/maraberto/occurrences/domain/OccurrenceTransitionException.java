package com.maraberto.occurrences.domain;

import java.util.UUID;

/**
 * O pedido de encerramento ou edição é inconsistente com o estado atual (por
 * exemplo, registrar CIOSP sem protocolo). Responde 409 para não confundir com
 * erro de validação de campo.
 */
public class OccurrenceTransitionException extends RuntimeException {

    private final transient UUID occurrenceId;
    private final String problemType;

    public OccurrenceTransitionException(UUID occurrenceId, String problemType, String message) {
        super(message);
        this.occurrenceId = occurrenceId;
        this.problemType = problemType;
    }

    public UUID occurrenceId() {
        return occurrenceId;
    }

    public String problemType() {
        return problemType;
    }
}
