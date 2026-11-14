package com.maraberto.operations.domain;

/**
 * Estado de triagem de um registro feito fora do mapeamento (RF12/RN17).
 *
 * <p>{@link #PENDING} é o estado de entrada: ninguém olhou ainda. As demais
 * são decisões da coordenação e por isso exigem autor e data:
 * {@link #IDENTIFIED} associa a um par área/setor que já existe,
 * {@link #NEEDS_MAPPING} registra que o local precisa entrar no KML e
 * {@link #DISCARDED} encerra o registro sem ação. Reabrir volta para
 * {@code PENDING}, limpando a decisão anterior.
 */
public enum UnmappedSignalStatus {
    PENDING,
    IDENTIFIED,
    NEEDS_MAPPING,
    DISCARDED
}
