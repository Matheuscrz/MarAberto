package com.maraberto.occupancy.domain;

/** Resultado de uma consulta de ocupação. */
public enum OccupancyStatus {
    /** Abaixo da amostra mínima (RN06): a UI mostra "dados insuficientes". */
    INSUFFICIENT,
    /** Há amostra suficiente para exibir uma faixa. */
    ESTIMATE
}
