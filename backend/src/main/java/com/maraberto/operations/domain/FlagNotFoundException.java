package com.maraberto.operations.domain;

/** Sinalização inexistente ou já encerrada. */
public class FlagNotFoundException extends RuntimeException {

    public FlagNotFoundException() {
        super("Sinalização não encontrada ou já encerrada.");
    }
}
