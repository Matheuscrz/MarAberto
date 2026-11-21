package com.maraberto.operations.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Página da lista de estruturas operacionais.
 *
 * <p>A ordem é {@code (kind, name, id)} — estável entre páginas: ativar ou
 * desativar uma estrutura não reordena a lista em andamento. O cliente devolve
 * {@code nextCursor} na próxima chamada e recebe as linhas seguintes sem
 * repetir nem pular registros.
 *
 * @param items      estruturas desta página
 * @param nextCursor cursor opaco da próxima página; {@code null} na última
 * @param hasMore    existe pelo menos mais uma linha depois desta página
 * @param total      total de estruturas que atendem ao filtro (sem cursor)
 */
@Schema(name = "StationPage", description = "Página de postos e bases")
public record StationPage(
        List<StationAdminView> items,
        String nextCursor,
        boolean hasMore,
        long total) {
}
