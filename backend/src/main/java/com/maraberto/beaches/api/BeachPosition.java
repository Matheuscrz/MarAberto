package com.maraberto.beaches.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Onde a pessoa está, em termos do mapeamento: a área e o setor que contêm a
 * posição enviada na lista por proximidade.
 *
 * <p>A coordenada só existe durante o request: não é gravada, logada nem usada
 * como chave de cache além da célula arredondada já existente. Fora de um setor
 * ativo o bloco vem nulo.
 *
 * @param sectorLabel rótulo pronto ("Setor N" quando o setor não tem nome)
 */
@Schema(name = "BeachPosition", description = "Área e setor que contêm a posição consultada")
public record BeachPosition(
        @Schema(description = "Área de banho que contém a posição") UUID beachId,
        @Schema(description = "Nome da área de banho") String beachName,
        @Schema(description = "Setor ativo que contém a posição") UUID sectorId,
        @Schema(description = "Rótulo do setor para exibição", example = "Setor Dunas")
        String sectorLabel) {
}
