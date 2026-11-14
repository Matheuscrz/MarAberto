package com.maraberto.operations.domain;

/**
 * Guarda-vidas tentou desativar/reactivar uma estrutura que não é o alvo do seu
 * plantão aberto.
 *
 * <p>O console de campo só alterna o posto/base onde a pessoa está agora. A
 * coordenação e a TI continuam podendo alterar qualquer estrutura.
 */
public class StationOutsideShiftException extends RuntimeException {

    public StationOutsideShiftException() {
        super("Você só pode alterar o posto ou a base do seu plantão aberto.");
    }
}
