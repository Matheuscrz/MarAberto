package com.maraberto.operations.domain;

/** Tipo de estrutura operacional exibida no mapa e no cadastro. */
public enum StationKind {

    /** Posto fixo: posição cadastrada, sem deslocamento. */
    FIXED,

    /** Base móvel: aparece na última posição do plantão, enquanto ele estiver vivo. */
    MOBILE
}
