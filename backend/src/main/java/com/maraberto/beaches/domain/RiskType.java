package com.maraberto.beaches.domain;

/**
 * Tipos de risco cobertos pelo mapa e pelos avisos de segurança.
 *
 * <p>Zonas críticas permanentes normalmente usam os riscos estruturais
 * ({@link #RIP_CURRENT}, {@link #SUBMERGED_DROP_OFF}, {@link #STRONG_WAVES},
 * {@link #ROCKS_AND_PIERS} e {@link #WHIRLPOOL_OR_DEEP_POOL}). Os demais tipos
 * também podem ser registrados quando a área precisar de um aviso delimitado.
 */
public enum RiskType {

    /** Corrente de retorno/repuxo: recuo forte da água em direção ao mar aberto. */
    RIP_CURRENT,

    /** Buraco ou valão submerso: mudança abrupta de profundidade sob a água. */
    SUBMERGED_DROP_OFF,

    /** Ondas fortes ou "caixote", que quebram na areia/beira com grande impacto. */
    STRONG_WAVES,

    /** Costões, pedras e piers: estruturas rígidas onde as ondas colidem. */
    ROCKS_AND_PIERS,

    /** Redemoinhos ou poços profundos, comuns em rios, lagoas e cachoeiras. */
    WHIRLPOOL_OR_DEEP_POOL,

    /** Presença de animais marinhos perigosos (águas-vivas, raias, tubarões etc.). */
    MARINE_ANIMALS,

    /** Água poluída ou imprópria para banho (esgoto, detritos, algas nocivas). */
    WATER_POLLUTION,

    /** Condições meteorológicas extremas, como raios e ventos muito fortes. */
    EXTREME_WEATHER,

    /** Outro risco não classificado nas categorias acima. */
    OTHER
}
