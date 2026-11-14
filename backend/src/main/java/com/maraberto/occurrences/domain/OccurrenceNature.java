package com.maraberto.occurrences.domain;

/**
 * Natureza do atendimento. O SOS não pergunta o motivo ao banhista, então a
 * ocorrência gerada a partir dele nasce como {@link #RESCUE} e a coordenação
 * ajusta na revisão.
 */
public enum OccurrenceNature {
    RESCUE,
    FIRST_AID,
    DROWNING,
    MISSING_PERSON,
    MARINE_ANIMAL,
    OTHER
}
