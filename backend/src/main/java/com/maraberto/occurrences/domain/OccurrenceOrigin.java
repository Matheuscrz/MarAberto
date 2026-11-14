package com.maraberto.occurrences.domain;

/**
 * Canal pelo qual o atendimento chegou ao sistema (RN11).
 */
public enum OccurrenceOrigin {
    /** Chamado de socorro do banhista, assumido por um guarda-vidas. */
    SOS_CITIZEN,
    /** Chamado repassado pelo 193. */
    CBM_193,
    /** Rádio, presencial ou outro posto — fora do 193 e do SOS. */
    EXTERNAL_OTHER,
    /** Atendimento feito pelo guarda-vidas sem chamado prévio. */
    FIELD_ATTENDED
}
