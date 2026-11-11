package com.maraberto.operations.domain;

/**
 * O operador já tem um plantão aberto ({@code uq_shift_open}). Encerrar o
 * anterior é ação explícita, não implícita: dois plantões simultâneos fariam o
 * mesmo operador receber o SOS duas vezes.
 */
public class ShiftAlreadyOpenException extends RuntimeException {

    public ShiftAlreadyOpenException() {
        super("O operador já possui um plantão aberto");
    }
}
