package com.maraberto.operations.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado do {@code POST /api/v1/operations/flags}.
 *
 * <p>O lançamento tem dois desfechos possíveis e ambos são sucesso ({@code 201}):
 * a bandeira foi plantada em uma área mapeada ({@code PLACED}, com o
 * {@code flag} preenchido) ou o ponto está fora do mapeamento e o registro foi
 * para a fila da coordenação ({@code UNMAPPED}, com {@code unmapped} preenchido).
 *
 * @param outcome  o que aconteceu
 * @param flag     sinalização publicada; nula em {@code UNMAPPED}
 * @param unmapped registro de área não identificada; nulo em {@code PLACED}
 */
@Schema(name = "FlagLaunchResponse", description = "Desfecho do lançamento de sinalização")
public record FlagLaunchResponse(
        LaunchOutcome outcome,
        FlagResponse flag,
        UnmappedSignalView unmapped) {

    public static FlagLaunchResponse placed(FlagResponse flag) {
        return new FlagLaunchResponse(LaunchOutcome.PLACED, flag, null);
    }

    public static FlagLaunchResponse unmapped(UnmappedSignalView signal) {
        return new FlagLaunchResponse(LaunchOutcome.UNMAPPED, null, signal);
    }
}
