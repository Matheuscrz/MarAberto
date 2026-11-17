package com.maraberto.occurrences.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Posição na lista de ocorrências para a paginação por cursor.
 *
 * <p>O par {@code (occurredAt, number)} é a chave estável da ordenação
 * {@code occurred_at DESC, number DESC}. Diferente de offset, ele não repete
 * nem pula registros quando uma ocorrência muda de status entre duas páginas.
 */
@Schema(name = "OccurrenceCursor",
        description = "Cursor da próxima página: horário do fato e número interno")
public record OccurrenceCursor(
        @Schema(description = "Horário do fato da última ocorrência devolvida")
        Instant occurredAt,
        @Schema(description = "Número interno da última ocorrência devolvida", example = "42")
        long number) {
}
