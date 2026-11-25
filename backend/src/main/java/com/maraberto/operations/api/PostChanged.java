package com.maraberto.operations.api;

import java.util.UUID;

/**
 * Uma estrutura do mapa público mudou: posto/base criado, reposicionado,
 * ativado ou desativado.
 *
 * <p>Assim como {@link FlagChanged}, o evento existe para invalidar o cache da
 * leitura pública e para quem projeta estruturas — não substitui a consulta.
 * O {@code kind} é a string estável {@code FIXED}/{@code MOBILE} para o evento
 * não expor o enum de domínio a outros módulos.
 *
 * @param stationId estrutura alterada
 * @param kind      {@code FIXED} ou {@code MOBILE}
 * @param active    estado depois da alteração
 */
public record PostChanged(UUID stationId, String kind, boolean active) {
}
