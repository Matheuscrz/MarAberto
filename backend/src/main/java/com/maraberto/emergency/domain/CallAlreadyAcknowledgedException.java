package com.maraberto.emergency.domain;

/**
 * Outro destinatário assumiu o chamado antes (perdeu o compare-and-set).
 */
public class CallAlreadyAcknowledgedException extends RuntimeException {

    public CallAlreadyAcknowledgedException() {
        super("O chamado já foi assumido por outro operador");
    }
}
