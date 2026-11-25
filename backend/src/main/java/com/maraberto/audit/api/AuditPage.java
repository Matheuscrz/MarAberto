package com.maraberto.audit.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Página da trilha, da mais recente para a mais antiga.
 *
 * <p>O cursor é opaco: devolva {@code nextCursor} em {@code cursor} para buscar
 * a página seguinte. A chave {@code (occurred_at, id)} não repete nem pula
 * linhas quando novos atos são gravados entre duas chamadas.
 */
@Schema(name = "AuditPage", description = "Página da trilha de auditoria")
public record AuditPage(
        List<AuditEntryView> items,
        @Schema(description = "Cursor opaco da próxima página; nulo na última")
        String nextCursor,
        boolean hasMore) {
}
