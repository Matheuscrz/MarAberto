package com.maraberto.occurrences.domain;

import java.util.UUID;

/**
 * Não há dados de vítima registrados para revelar. A ficha usa 404 para não
 * distinguir "não existe" de "não está no seu escopo" (RN15).
 */
public class OccurrenceVictimNotFoundException extends RuntimeException {

    private final transient UUID occurrenceId;

    public OccurrenceVictimNotFoundException(UUID occurrenceId) {
        super("Nenhum dado de vítima registrado nesta ocorrência.");
        this.occurrenceId = occurrenceId;
    }

    public UUID occurrenceId() {
        return occurrenceId;
    }
}
