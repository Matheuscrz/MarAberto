package com.maraberto.operations.domain;

/** Registro de área não identificada inexistente para a triagem. */
public class UnmappedSignalNotFoundException extends RuntimeException {

    public UnmappedSignalNotFoundException() {
        super("Registro de área não identificada não encontrado");
    }
}
