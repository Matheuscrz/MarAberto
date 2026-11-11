package com.maraberto.operations.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Posto fixo ou base móvel ativo que o operador pode escolher ao abrir o
 * plantão.
 *
 * <p>A lista mostra apenas estruturas ativas: abrir plantão em posto/base
 * inativo deixaria o operador fora da seleção do SOS sem aviso.
 *
 * @param id                  identificador do posto ou da base
 * @param kind                tipo da estrutura
 * @param name                nome exibido no console
 * @param beachName           praia do posto fixo; nulo para base móvel
 * @param latitude            latitude WGS84 da estrutura
 * @param longitude           longitude WGS84 da estrutura
 * @param coverageRadiusMeters raio cadastrado; nulo usa o padrão da instalação
 */
@Schema(name = "ShiftTarget", description = "Posto fixo ou base móvel disponível para plantão")
public record ShiftTarget(
        UUID id,
        ShiftTargetKind kind,
        String name,
        String beachName,
        double latitude,
        double longitude,
        Integer coverageRadiusMeters) {
}
