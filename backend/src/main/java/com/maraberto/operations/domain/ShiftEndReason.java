package com.maraberto.operations.domain;

/**
 * Motivo do encerramento do plantão.
 *
 * <p>Distingue o encerramento pelo próprio operador de um plantão que caiu
 * porque o app foi fechado (ou o aparelho perdeu sinal): o painel da coordenação
 * precisa saber que aquele turno terminou sem confirmação humana.
 */
public enum ShiftEndReason {

    /** O operador encerrou o plantão na tela. */
    MANUAL,

    /** O console parou de enviar sinal de vida (app fechado, sem rede ou sem GPS). */
    IDLE_TIMEOUT,

    /** O operador saiu da conta; o plantão não sobrevive ao logout. */
    LOGOUT
}
