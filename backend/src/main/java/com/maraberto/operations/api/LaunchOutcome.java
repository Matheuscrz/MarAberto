package com.maraberto.operations.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * O que aconteceu com o lançamento da bandeira.
 *
 * <p>{@link #PLACED}: a bandeira foi publicada na área/setor resolvido.
 * {@link #UNMAPPED}: o ponto está fora do mapeamento, então o lançamento virou
 * um registro de área não identificada para a coordenação triar — nenhuma
 * bandeira aparece no mapa público.
 */
@Schema(name = "LaunchOutcome", description = "Resultado do lançamento de sinalização")
public enum LaunchOutcome {
    PLACED,
    UNMAPPED
}
