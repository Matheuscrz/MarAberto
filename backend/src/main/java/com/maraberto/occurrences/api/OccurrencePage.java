package com.maraberto.occurrences.api;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Página de ocorrências ordenada da mais recente para a mais antiga.
 *
 * <p>{@code nextCursor} só vem quando existe uma página seguinte. Basta
 * devolvê-lo como {@code beforeOccurredAt}/{@code beforeNumber} na próxima
 * chamada; nenhum registro é repetido nem pulado quando o status muda.
 */
@Schema(name = "OccurrencePage", description = "Página de ocorrências do console")
public record OccurrencePage(
        @ArraySchema(schema = @Schema(implementation = OccurrenceView.class))
        List<OccurrenceView> items,
        @Schema(description = "Cursor da próxima página; nulo quando esta é a última")
        OccurrenceCursor nextCursor,
        @Schema(description = "Há registros mais antigos além desta página")
        boolean hasMore) {
}
