package com.maraberto.occurrences.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Dados de vítima da ocorrência.
 *
 * <p>Sem revelação explícita, só {@code recorded} é confiável: os campos vêm
 * nulos e o console nunca recebe o dado em claro. A revelação grava
 * {@code PERSON_REVEALED} no histórico da ocorrência (RN15).
 */
@Schema(name = "OccurrenceVictim", description = "Dados de vítima, opcionais e cifrados")
public record VictimView(
        @Schema(description = "Há dados de vítima registrados") boolean recorded,
        @Schema(description = "A revelação foi autorizada nesta resposta") boolean revealed,
        String name,
        String age,
        String contact,
        String condition) {

    public static VictimView hidden(boolean recorded) {
        return new VictimView(recorded, false, null, null, null, null);
    }

    public static VictimView revealed(
            String name, String age, String contact, String condition) {
        return new VictimView(true, true, name, age, contact, condition);
    }
}
