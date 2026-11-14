package com.maraberto.operations.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Posto fixo ou base móvel ativo que o operador pode escolher ao abrir o
 * plantão.
 *
 * <p>A lista mostra apenas estruturas ativas: abrir plantão em posto/base
 * inativo deixaria o operador fora da seleção do SOS sem aviso. A posição é a
 * da estrutura (cadastro/reposição), com a data e a precisão registradas para o
 * operador saber onde o ponto fica e há quanto tempo foi informado.
 *
 * @param id                  identificador do posto ou da base
 * @param kind                tipo da estrutura
 * @param name                nome exibido no console
 * @param beachName           praia do posto fixo; nulo para base móvel
 * @param latitude            latitude WGS84 da estrutura
 * @param longitude           longitude WGS84 da estrutura
 * @param positionUpdatedAt   quando a posição da estrutura foi registrada
 * @param positionAccuracyM   precisão informada pela leitura do aparelho
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
        Instant positionUpdatedAt,
        Double positionAccuracyM,
        Integer coverageRadiusMeters) {
}
