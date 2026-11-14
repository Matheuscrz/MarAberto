package com.maraberto.occurrences.domain;

import java.util.UUID;

/**
 * Ocorrência inexistente ou fora do escopo de quem consulta. As duas situações
 * respondem {@code 404}: revelar que existe uma ocorrência em outra praia já
 * seria vazamento de escopo (RN15).
 */
public class OccurrenceNotFoundException extends RuntimeException {

    private final transient UUID occurrenceId;

    public OccurrenceNotFoundException(UUID occurrenceId) {
        super("Ocorrência não encontrada.");
        this.occurrenceId = occurrenceId;
    }

    public UUID occurrenceId() {
        return occurrenceId;
    }
}
