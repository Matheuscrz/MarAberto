package com.maraberto.operations.api;

import java.util.UUID;

/**
 * A sinalização pública mudou: uma bandeira foi lançada ou encerrada.
 *
 * <p>O evento é publicado na mesma transação do lançamento e consumido depois
 * do commit. Ele não carrega o detalhe da bandeira: o mapa público continua
 * lendo a lista pela API, e o evento serve para invalidar o cache e acordar
 * quem projeta a sinalização. A vigência por tempo não gera evento — o polling
 * do cliente e o TTL curto do cache cobrem o vencimento.
 *
 * @param flagId   lançamento que mudou
 * @param beachId  área de banho do lançamento
 * @param sectorId setor, quando o lançamento é de setor; nulo na praia inteira
 */
public record FlagChanged(UUID flagId, UUID beachId, UUID sectorId) {
}
