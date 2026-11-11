package com.maraberto.operations.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Destinatário possível de um SOS: um plantão aberto dentro (ou próximo) do
 * raio de cobertura.
 *
 * <p>A distância é medida em metros, a partir da última posição conhecida do
 * plantão. {@code positionStale} sinaliza base móvel sem posição recente — o
 * chamado continua sendo despachado, mas o console mostra a idade do dado.
 *
 * @param userId          operador que recebe a notificação
 * @param shiftSessionId  plantão aberto de origem
 * @param postId          posto fixo, quando o plantão é de posto
 * @param baseId          base móvel, quando o plantão é de base
 * @param distanceMeters  distância em metros; nulo quando o destinatário é a
 *                        coordenação central (sem posição em campo)
 * @param positionStale   última posição conhecida acima do limite configurado
 */
@Schema(name = "EligibleResponder", description = "Plantão aberto elegível para receber o SOS")
public record EligibleResponder(
        UUID userId,
        UUID shiftSessionId,
        UUID postId,
        UUID baseId,
        Double distanceMeters,
        boolean positionStale) {

    public static EligibleResponder coordination(UUID userId) {
        return new EligibleResponder(userId, null, null, null, null, false);
    }
}
