package com.maraberto.operations.domain;

/**
 * De onde veio a coordenada da estrutura (RF11).
 *
 * <p>{@code DEVICE_GPS} é a posição atual do aparelho no momento do cadastro ou
 * da reposição; {@code MANUAL} é o cadastro antigo; {@code MAP_PICK} é o ponto
 * escolhido no mapa pela coordenação. O painel mostra a origem e a precisão
 * para julgar a confiança do ponto.
 */
public enum StationPositionSource {
    MANUAL,
    DEVICE_GPS,
    MAP_PICK
}
