package com.maraberto.operations.domain;

/**
 * Não há plantão aberto para o operador autenticado.
 */
public class ShiftNotFoundException extends RuntimeException {

    public ShiftNotFoundException() {
        super("Nenhum plantão aberto para o operador");
    }
}
