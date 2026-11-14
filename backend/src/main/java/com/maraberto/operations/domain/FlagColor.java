package com.maraberto.operations.domain;

/**
 * Cores da sinalização de bandeira adotada pelos guarda-vidas (RF12).
 *
 * <p>O histórico é append-only: cada lançamento é uma linha em
 * {@code flag_updates} com validade própria (RN01). O vocabulário mora no
 * módulo que escreve a sinalização — o mapa público apenas o consome.
 */
public enum FlagColor {

    /** Baixo risco: mar calmo, mantidos os cuidados básicos. */
    GREEN,

    /** Médio risco: mar agitado ou correntes moderadas; atenção redobrada. */
    YELLOW,

    /** Alto risco: condições perigosas; em um ponto específico, indica corrente de retorno. */
    RED,

    /** Animais marinhos perigosos (águas-vivas, caravelas, raias, tubarões etc.). */
    PURPLE,

    /** Área interditada ou poluição severa. */
    BLACK
}
