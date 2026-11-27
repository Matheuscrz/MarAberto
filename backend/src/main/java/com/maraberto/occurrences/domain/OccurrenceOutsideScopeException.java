package com.maraberto.occurrences.domain;

/**
 * Operação sobre uma ocorrência fora do escopo territorial do operador (403).
 */
public class OccurrenceOutsideScopeException extends RuntimeException {

    public OccurrenceOutsideScopeException() {
        super("Área de banho fora do seu escopo territorial.");
    }
}
