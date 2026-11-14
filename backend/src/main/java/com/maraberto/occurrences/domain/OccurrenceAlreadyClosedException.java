package com.maraberto.occurrences.domain;

import java.util.UUID;

/**
 * A ocorrência já foi encerrada e não aceita um novo encerramento. A edição
 * dos dados continua permitida (CIOSP, vítima identificada depois).
 */
public class OccurrenceAlreadyClosedException extends RuntimeException {

    private final transient UUID occurrenceId;

    public OccurrenceAlreadyClosedException(UUID occurrenceId) {
        super("Ocorrência já encerrada. Edite os dados para completar o registro.");
        this.occurrenceId = occurrenceId;
    }

    public UUID occurrenceId() {
        return occurrenceId;
    }
}
