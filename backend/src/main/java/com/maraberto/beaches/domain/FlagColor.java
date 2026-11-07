package com.maraberto.beaches.domain;

/**
 * Cores da sinalização de bandeira adotada pelos guarda-vidas.
 *
 * <p>O lançamento/histórico de bandeiras será modelado na issue de operações
 * (tabela {@code flag_updates}); este enum apenas fixa o vocabulário do domínio.
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
